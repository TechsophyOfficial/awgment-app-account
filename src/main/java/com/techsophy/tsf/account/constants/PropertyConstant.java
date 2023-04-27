package com.techsophy.tsf.account.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PropertyConstant {
    public static final String INTERNAL_URL = "/internal/";
    public static final String INTERNAL_ANT_MATCHER = "/internal/**";
    public static final String X_SIGNATURE = "X-Signature";
    public static final String KEY_PREFIX = "-----BEGIN PUBLIC KEY-----";
    public static final String KEY_SUFFIX = "-----END PUBLIC KEY-----";
    public static final String RSA = "RSA";
    public static final String SIGN_INSTANCE = "NONEwithRSA";
    public static final String KEY_LOCATION_VALUE = "src/test/resources/testdata/public_key_rsa_4096_for_test.pem";
    public static final String KEY_LOCATION = "keyLocation";
}
