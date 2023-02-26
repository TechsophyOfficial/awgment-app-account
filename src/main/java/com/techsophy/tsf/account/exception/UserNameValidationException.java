package com.techsophy.tsf.account.exception;

public class UserNameValidationException extends RuntimeException
{
    final String errorcode;
    final String message;
    public UserNameValidationException(String errorCode, String message)
    {
        super(message);
        this.errorcode= errorCode;
        this.message=message;
    }
}
