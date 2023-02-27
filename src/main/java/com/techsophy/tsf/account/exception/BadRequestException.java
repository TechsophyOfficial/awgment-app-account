package com.techsophy.tsf.account.exception;

public class BadRequestException extends RuntimeException
{
    final String errorcode;
    final String message;
    public BadRequestException(String errorcode, String message) {
        super(message);
        this.errorcode = errorcode;
        this.message = message;
    }

}
