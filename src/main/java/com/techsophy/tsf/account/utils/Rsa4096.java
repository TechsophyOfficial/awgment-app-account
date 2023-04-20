package com.techsophy.tsf.account.utils;

import com.techsophy.tsf.account.config.GlobalMessageSource;
import com.techsophy.tsf.account.dto.UserFormDataSchema;
import com.techsophy.tsf.account.exception.BadRequestException;
import com.techsophy.tsf.account.exception.RunTimeException;
import com.techsophy.tsf.account.exception.UnAuthorizedException;
import javax.validation.ConstraintViolationException;
import java.io.IOException;
import java.io.InputStream;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

import static com.techsophy.tsf.account.constants.ErrorConstants.UN_AUTHORIZED_EXCEPTION;

public class Rsa4096 {
    private GlobalMessageSource globalMessageSource;
    private KeyFactory keyFactory;
    private PublicKey publicKey;
    public Rsa4096() throws NoSuchAlgorithmException, IOException, InvalidKeySpecException {
        setKeyFactory();
        setPublicKey("keys/public_key_rsa_4096_pkcs8-exported.pem");
    }

    protected void setKeyFactory() throws NoSuchAlgorithmException {
        this.keyFactory = KeyFactory.getInstance("RSA");
    }

    protected void setPublicKey(String classpathResource)
            throws IOException, InvalidKeySpecException {
        InputStream is = this
                .getClass()
                .getClassLoader()
                .getResourceAsStream(classpathResource);

        String stringBefore = new String(is.readAllBytes());
        is.close();

        String stringAfter = stringBefore
                .replace("\n", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .trim();

        byte[] decoded = Base64
                .getDecoder()
                .decode(stringAfter);

        KeySpec keySpec
                = new X509EncodedKeySpec(decoded);

        publicKey = keyFactory.generatePublic(keySpec);
    }

    public Boolean verifySignature(String signature, String value) throws SignatureException, InvalidKeyException, NoSuchAlgorithmException {
        Signature sign = Signature.getInstance("NONEwithRSA");
        byte[] digitalSignature = Base64
                .getDecoder()
                .decode(signature);
        //Initializing the signature
        sign.initVerify(publicKey);
        byte[] bytes = value.getBytes();
        sign.update(bytes);

        //Verifying the signature
        return sign.verify(digitalSignature);
    }

    public UserFormDataSchema transform(String headerSign,UserFormDataSchema internalUserFormDataSchema)
    {
        try
        {
            Rsa4096 rsa4096 = new Rsa4096();
            String signatureValue = (String)internalUserFormDataSchema.getUserData().get("userName");
            Boolean isVerified = rsa4096.verifySignature(headerSign,signatureValue);
            if(isVerified != null && !isVerified)
            {
                throw new UnAuthorizedException(UN_AUTHORIZED_EXCEPTION,globalMessageSource.get(UN_AUTHORIZED_EXCEPTION));
            }
            return internalUserFormDataSchema;
        }
        catch (ConstraintViolationException | BadRequestException e)
        {
            throw e;
        }
        catch (Exception e) {
            throw new RunTimeException(e.getMessage());
        }
    }
}


