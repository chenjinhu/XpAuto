#!/usr/bin/env python3
"""检查最终 APK/AAB 中的原生库，防止第三方依赖或构建参数退回 4 KB 对齐。"""
import argparse
import struct
import sys
import zipfile
from pathlib import Path

PAGE_SIZE = 16384
PT_LOAD = 1
PT_GNU_RELRO = 0x6474E552


def program_headers(data):
    """只读取 ELF 公开格式；不依赖本机 NDK，便于在构建机执行。"""
    if data[:4] != b"\x7fELF" or data[5] != 1:
        raise ValueError("不是受支持的小端 ELF 文件")
    if data[4] == 2:
        offset = struct.unpack_from("<Q", data, 32)[0]
        size, count = struct.unpack_from("<HH", data, 54)
        layout = "<IIQQQQQQ"
    elif data[4] == 1:
        offset = struct.unpack_from("<I", data, 28)[0]
        size, count = struct.unpack_from("<HH", data, 42)
        layout = "<IIIIIIII"
    else:
        raise ValueError("未知 ELF 位数")
    if not count or size < struct.calcsize(layout) or offset + size * count > len(data):
        raise ValueError("ELF 程序头不完整")
    result = []
    for index in range(count):
        values = struct.unpack_from(layout, data, offset + index * size)
        if data[4] == 2:
            kind, flags, pos, address, _, file_size, memory_size, alignment = values
        else:
            kind, pos, address, _, file_size, memory_size, flags, alignment = values
        if pos + file_size > len(data) or memory_size < file_size:
            raise ValueError("ELF 段越界或大小无效")
        result.append(dict(kind=kind, flags=flags, offset=pos, address=address,
                           file_size=file_size, memory_size=memory_size, alignment=alignment))
    return result


def elf_errors(data):
    headers = program_headers(data)
    loads = [entry for entry in headers if entry["kind"] == PT_LOAD]
    errors = []
    if not loads:
        return ["没有 LOAD 段"]
    for entry in loads:
        alignment = entry["alignment"]
        if alignment < PAGE_SIZE or alignment & (alignment - 1):
            errors.append(f"LOAD 对齐为 {alignment}，应至少为 16384 且为 2 的幂")
        if (entry["address"] - entry["offset"]) % PAGE_SIZE:
            errors.append("LOAD 文件偏移与虚拟地址不满足 16 KB 同余")
    for relro in (entry for entry in headers if entry["kind"] == PT_GNU_RELRO):
        end = relro["address"] + relro["memory_size"]
        # RELRO 结尾可以不在页边界，但向上取整后的保护范围不能覆盖后续可写数据。
        # Javet 的 RELRO 使用独立 LOAD 段，不能仅因结尾未对齐就误判失败。
        protected_end = (end + PAGE_SIZE - 1) // PAGE_SIZE * PAGE_SIZE
        for entry in loads:
            if not entry["flags"] & 2:
                continue
            writable_start = max(end, entry["address"])
            writable_end = min(protected_end, entry["address"] + entry["memory_size"])
            if writable_start < writable_end:
                errors.append("RELRO 末尾保护范围与后续可写数据共享 16 KB 页面")
                break
    return list(dict.fromkeys(errors))


def zip_data_offset(stream, entry):
    stream.seek(entry.header_offset)
    header = stream.read(30)
    if header[:4] != b"PK\x03\x04":
        raise ValueError("ZIP 本地文件头无效")
    name_size, extra_size = struct.unpack_from("<HH", header, 26)
    return entry.header_offset + 30 + name_size + extra_size


def verify(path):
    failures, count = 0, 0
    with zipfile.ZipFile(path) as archive, path.open("rb") as stream:
        for entry in archive.infolist():
            if not entry.filename.endswith(".so"):
                continue
            count += 1
            try:
                errors = elf_errors(archive.read(entry))
                # AAB 的库由 bundletool 重新打包，此处只检查 APK 中直接 mmap 的库位置。
                if path.suffix == ".apk" and entry.compress_type == zipfile.ZIP_STORED:
                    if zip_data_offset(stream, entry) % PAGE_SIZE:
                        errors.append("APK 内未压缩库的 ZIP 数据偏移未按 16 KB 对齐")
            except (ValueError, struct.error, IndexError) as error:
                errors = [str(error)]
            if errors:
                failures += 1
                print(f"FAIL {entry.filename}: {'；'.join(errors)}")
            else:
                print(f"PASS {entry.filename}")
    if not count:
        raise ValueError("产物不包含原生库，请确认输入路径")
    print(f"{path.name}: {count} 个库，{failures} 个不满足 16 KB 对齐")
    return failures == 0


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifacts", type=Path, nargs="+", help="要验证的 APK 或 AAB")
    args = parser.parse_args()
    success = True
    for path in args.artifacts:
        try:
            success = verify(path) and success
        except (OSError, ValueError, zipfile.BadZipFile) as error:
            print(f"FAIL {path}: {error}", file=sys.stderr)
            success = False
    return 0 if success else 1


if __name__ == "__main__":
    sys.exit(main())
