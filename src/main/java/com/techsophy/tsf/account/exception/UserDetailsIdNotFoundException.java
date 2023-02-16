package com.techsophy.tsf.account.exception;

public class UserDetailsIdNotFoundException extends RuntimeException
{
    final String errorcode;
    final String message;
    public UserDetailsIdNotFoundException(String errorCode, String message)
    {
        super(message);
        this.errorcode= errorCode;
        this.message=message;
    }
}
