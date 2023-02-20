package com.techsophy.tsf.account.constants;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import javax.validation.constraints.NotNull;
import java.math.BigInteger;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserFormDataConstants
{
    public final static @NotNull(message = "Id should not be null") String ID = String.valueOf(BigInteger.valueOf(1));
    public final static @NotNull(message = "Id should not be null") String userID = String.valueOf(BigInteger.valueOf(1));
    public final static String ANYSTRING="abc";
}
