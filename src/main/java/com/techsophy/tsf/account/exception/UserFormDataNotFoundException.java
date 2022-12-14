package com.techsophy.tsf.account.exception;

public class UserFormDataNotFoundException extends RuntimeException
{
    final String errorcode;
    final String message;
    public UserFormDataNotFoundException(String errorcode,String message)
    {
        super(message);
        this.errorcode=errorcode;
        this.message=message;
    }
}
