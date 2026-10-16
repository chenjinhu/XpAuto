package com.vixmobi.auto.toolbox;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** 兼容函数名；参考文档未定义密钥派生，XpAuto 的固定规则见工具函数文档。 */
public final class EncodeDecoder {
  private final ThreadLocal<String> error = new ThreadLocal<>();

  public String getErrorMsg() {
    return error.get();
  }

  private String crypt(String data, String password, String algorithm, boolean encrypt) {
    try {
      error.remove();
      if (password == null || password.codePointCount(0, password.length()) < 8)
        throw new IllegalArgumentException("密码至少需要 8 个字符");
      byte[] raw = password.getBytes(StandardCharsets.UTF_8);
      int length = algorithm.equals("AES") ? 16 : algorithm.equals("DES") ? 8 : 24;
      // 长度合适时直接使用 UTF-8 密钥；其他密码用 SHA-256 后取对应长度。
      boolean valid =
          raw.length == length || algorithm.equals("AES") && (raw.length == 24 || raw.length == 32);
      byte[] key =
          valid ? raw : Arrays.copyOf(MessageDigest.getInstance("SHA-256").digest(raw), length);
      boolean triple = algorithm.equals("DESede");
      Cipher cipher =
          Cipher.getInstance(algorithm + (triple ? "/CBC/PKCS5Padding" : "/ECB/PKCS5Padding"));
      SecretKeySpec secret = new SecretKeySpec(key, algorithm);
      if (triple)
        cipher.init(
            encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE,
            secret,
            new IvParameterSpec(new byte[8]));
      else cipher.init(encrypt ? Cipher.ENCRYPT_MODE : Cipher.DECRYPT_MODE, secret);
      byte[] result =
          cipher.doFinal(
              encrypt ? data.getBytes(StandardCharsets.UTF_8) : Base64.getDecoder().decode(data));
      return encrypt
          ? Base64.getEncoder().encodeToString(result)
          : new String(result, StandardCharsets.UTF_8);
    } catch (Exception exception) {
      error.set(exception.getMessage());
      return null;
    }
  }

  public String aesEncrypt(String data, String password) {
    return crypt(data, password, "AES", true);
  }

  public String aesDecrypt(String data, String password) {
    return crypt(data, password, "AES", false);
  }

  public String desEncrypt(String data, String password) {
    return crypt(data, password, "DES", true);
  }

  public String desDecrypt(String data, String password) {
    return crypt(data, password, "DES", false);
  }

  public String des3Encrypt(String data, String password) {
    return crypt(data, password, "DESede", true);
  }

  public String des3Decrypt(String data, String password) {
    return crypt(data, password, "DESede", false);
  }
}
