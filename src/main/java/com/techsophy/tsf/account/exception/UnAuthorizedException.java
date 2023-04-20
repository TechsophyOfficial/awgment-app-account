package com.techsophy.tsf.account.exception;

public class UnAuthorizedException extends RuntimeException
{
    final String errorcode;
    final String message;
    public UnAuthorizedException(String errorcode, String message) {
        super(message);
        this.errorcode = errorcode;
        this.message = message;
    }
}
