package com.techsophy.tsf.account.constants;

public class PropertyConstant {
    public static final String ADD_USER_ENDPOINT = "http://localhost:8084/internal/v1/users/create";
    public static final String API_SIGNATURE_KEY = "signature";
    public static final String PUBLIC_KEY_FILE = "keys/public_key_rsa_4096_pkcs8-exported.pem";
    public static final String KEY_FACTORY = "RSA";
    public static final String KEY_PREFIX = "-----BEGIN PRIVATE KEY-----";
    public static final String KEY_SUFFIX = "-----END PRIVATE KEY-----";
}
