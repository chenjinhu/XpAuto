function double(value) {
    return value * 2;
}

function calculateTotal(start) {
    const increment = 7;
    // 在下一行设置断点；主动暂停可使用 debugger;。
    let total = start + increment; // breakpoint
    total = double(total);
    return total;
}

globalThis.probeResult = calculateTotal(5);
logi('计算完成，probeResult =', probeResult);
