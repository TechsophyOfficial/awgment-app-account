package com.techsophy.tsf.account.exception;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.model.ApiErrorResponse;
import lombok.AllArgsConstructor;
import org.apache.tomcat.util.http.fileupload.impl.FileSizeLimitExceededException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import javax.security.auth.login.AccountNotFoundException;
import javax.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Locale;
import static com.techsophy.tsf.account.constants.ErrorConstants.FILE_SIZE_EXCEEDED;

@ControllerAdvice
@AllArgsConstructor
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler
{
    private final GlobalMessageSource globalMessageSource;

    @ExceptionHandler(CreateFormDataException.class)
    public ResponseEntity<ApiErrorResponse> handleCreateFormDataException(CreateFormDataException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), getMessage(ex.message, null, request.getLocale()), ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(RunTimeException.class)
    public ResponseEntity<ApiErrorResponse> runTimeException(RunTimeException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), getMessage(ex.getMessage(), null, request.getLocale()),ex.getMessage(),
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(CreateTenantException.class)
    public ResponseEntity<ApiErrorResponse> handleCreateTenantException(CreateTenantException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), getMessage(ex.message, null, request.getLocale()), ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiErrorResponse> handleDomainException(DomainException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), getMessage(ex.message, null, request.getLocale()), ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ApiErrorResponse> handleInvalidInputException(InvalidInputException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), getMessage(ex.message, null, request.getLocale()), ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    private String getMessage(String errorCode, String[] args, Locale locale)
    {
        return globalMessageSource.get(errorCode, args, locale);
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleUserException(UserNotFoundException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(EntityNotFoundByIdException.class)
    public ResponseEntity<ApiErrorResponse> entityNotFoundException(EntityNotFoundByIdException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(EntityIdNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> entityIdNotFoundException(EntityIdNotFoundException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> methodArgumentNotValid(ConstraintViolationException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.getMessage(), ex.getMessage(),
                HttpStatus.BAD_REQUEST, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<ApiErrorResponse> methodArgumentNotValid(DataAccessException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.getMessage(), ex.getMessage(),
                HttpStatus.BAD_REQUEST, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.BAD_REQUEST);
    }
    @ExceptionHandler(UserFormDataNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleFormException(UserFormDataNotFoundException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RolesNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleRolesException(RolesNotFoundException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(RealmAcessException.class)
    public ResponseEntity<ApiErrorResponse> handleRealmException(RealmAcessException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<ApiErrorResponse> handleRealmException(MailException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(GroupsNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleGroupsException(GroupsNotFoundException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(UserPreferencesNotFoundByLoggedInUserIdException.class)
    public ResponseEntity<ApiErrorResponse> handleUserPreferencesNotFOundByLoggedInUserIdException(UserPreferencesNotFoundByLoggedInUserIdException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(ProfilePictureNotPresentException.class)
    public ResponseEntity<ApiErrorResponse> handleProfilePictureNotPresentException(ProfilePictureNotPresentException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(NoDataFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProfilePictureNotPresentException(NoDataFoundException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(BulkUserNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProfilePictureNotPresentException(BulkUserNotFoundException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorCode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(InvalidDataException.class)
    public ResponseEntity<ApiErrorResponse> handleProfilePictureNotPresentException(InvalidDataException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(InvalidProfilePictureException.class)
    public ResponseEntity<ApiErrorResponse> handleProfilePictureNotPresentException(InvalidProfilePictureException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(ServiceNotEnabledException.class)
    public ResponseEntity<ApiErrorResponse> handleProfilePictureNotPresentException(ServiceNotEnabledException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(ThemesNotFoundByIdException.class)
    public ResponseEntity<ApiErrorResponse> handleProfilePictureNotPresentException(ThemesNotFoundByIdException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorCode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
    @ExceptionHandler(UserDetailsIdNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProfilePictureNotPresentException(UserDetailsIdNotFoundException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(), ex.message, ex.errorcode,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    @ExceptionHandler(FileSizeLimitExceededException.class)
    public ResponseEntity<ApiErrorResponse> handleProfilePictureNotPresentException(FileSizeLimitExceededException ex, WebRequest request)
    {
        ApiErrorResponse errorDetails = new ApiErrorResponse(Instant.now(),globalMessageSource.get(FILE_SIZE_EXCEEDED),FILE_SIZE_EXCEEDED,
                HttpStatus.INTERNAL_SERVER_ERROR, request.getDescription(false));
        return new ResponseEntity<>(errorDetails, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
