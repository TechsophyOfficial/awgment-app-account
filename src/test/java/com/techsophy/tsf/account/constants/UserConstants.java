package com.techsophy.tsf.account.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserConstants
{
    public final static @NotNull(message = "Id should not be null") String ID = String.valueOf(BigInteger.valueOf(1));
    public final static @NotNull(message = "userId should not be null") String userID = String.valueOf(BigInteger.valueOf(1));
    public final static String NAME = "AccountTest";
    public final static String ROLE_ID ="5aa92c95-310e-4411-a031-d0d0f6c5b30d";
    public final static String ROLE_NAME="test";
    public final static String USER_STRING="user";

    //GlobalMessageSourceConstants
    public static final String  KEY="key";
    public static final String ARGS="args";
}
