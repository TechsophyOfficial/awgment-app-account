package com.techsophy.tsf.account.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PropertyConstant {
    public static final String INTERNAL_URL = "/internal/";
    public static final String INTERNAL_ANT_MATCHER = "/internal/**";
    public static final String X_SIGNATURE = "X-Signature";
}
