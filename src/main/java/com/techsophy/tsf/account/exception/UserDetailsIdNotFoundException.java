package com.techsophy.tsf.account.exception;

public class UserDetailsIdNotFoundException extends RuntimeException
{
    final String errorcode;
    final String message;
    public UserDetailsIdNotFoundException(String errorcode, String message)
    {
        super(message);
        this.errorcode= errorcode;
        this.message=message;
    }
}
