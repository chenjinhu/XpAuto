package com.vixmobi.auto.toolbox;

import static org.junit.Assert.*;

import org.junit.Test;

/** 校验公开密钥规则、UTF-8 往返和错误恢复，避免只能自加自解却不互通。 */
public class EncodeDecoderTest {
  @Test
  public void aesMatchesKnownExternalCiphertext() {
    EncodeDecoder codec = new EncodeDecoder();
    // OpenSSL AES-128-ECB，UTF-8 key="1234567890123456"，PKCS#7 padding。
    assertEquals("67fHA+Z12z2jlwOLTBeCPA==", codec.aesEncrypt("hello", "1234567890123456"));
  }

  @Test
  public void allCiphersRoundTripUnicodeAndRecoverFromError() {
    EncodeDecoder codec = new EncodeDecoder();
    String text = "中文\nemoji 😀";
    assertEquals(text, codec.aesDecrypt(codec.aesEncrypt(text, "12345678"), "12345678"));
    assertEquals(text, codec.desDecrypt(codec.desEncrypt(text, "12345678"), "12345678"));
    assertEquals(text, codec.des3Decrypt(codec.des3Encrypt(text, "12345678"), "12345678"));
    assertNull(codec.aesDecrypt("bad base64", "12345678"));
    assertNotNull(codec.getErrorMsg());
    assertNotNull(codec.aesEncrypt(text, "12345678"));
    assertNull(codec.getErrorMsg());
    assertNull(codec.aesEncrypt(text, "short"));
    assertNotNull(codec.getErrorMsg());
  }
}
