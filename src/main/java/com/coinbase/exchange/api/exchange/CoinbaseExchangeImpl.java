package com.coinbase.exchange.api.exchange;

import com.coinbase.exchange.api.accounts.Account;
import com.coinbase.exchange.api.constants.GdaxConstants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.management.RuntimeErrorException;
import java.lang.reflect.Type;
import java.security.InvalidKeyException;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;

import static org.springframework.http.HttpMethod.GET;


/**
 * Created by irufus on 2/25/15.
 */
@Component
public class CoinbaseExchangeImpl implements CoinbaseExchange {

    String publicKey;
    String secretKey;
    String passphrase;
    String baseUrl;

    @Autowired
    RestTemplate restTemplate;

    @Autowired
    public CoinbaseExchangeImpl(@Value("${gdax.key}") String publicKey,
                                @Value("${gdax.secret}") String secretKey,
                                @Value("${gdax.passphrase}") String passphrase,
                                @Value("${gdax.api.baseUrl}") String baseUrl) {
        this.publicKey = publicKey;
        this.secretKey = secretKey;
        this.passphrase = passphrase;
        this.baseUrl = baseUrl;
    }

    @Override
    public <T> T get(String resourcePath, ParameterizedTypeReference<T> responseType) {
        ResponseEntity<T> responseEntity = restTemplate.exchange( getBaseUrl() + resourcePath,
                GET, securityHeaders(resourcePath, "GET", ""), responseType);

        return responseEntity.getBody();
    }

    @Override
    public <T> T delete(String resourcePath, ParameterizedTypeReference<T> responseType) {
        ResponseEntity<T> response = restTemplate.exchange(getBaseUrl() + resourcePath,
                HttpMethod.DELETE,
                securityHeaders(resourcePath, "DELETE", ""),
                responseType);
        return response.getBody();
    }

    @Override
    public <T> T post(String resourcePath,  ParameterizedTypeReference<T> responseType, String jsonBody) {
        ResponseEntity<T> response = restTemplate.exchange(getBaseUrl() + resourcePath,
                HttpMethod.POST,
                securityHeaders(resourcePath, "POST", jsonBody),
                responseType);
        return response.getBody();
    }

    @Override
    public String getBaseUrl() {
        return baseUrl;
    }

    @Override
    public HttpEntity<String> securityHeaders(String endpoint, String method, String body) {
        HttpHeaders headers = new HttpHeaders();
        String timestamp = Instant.now().getEpochSecond() + "";
        String resource = endpoint.replace(getBaseUrl(), "");

        headers.add("accept", "application/json");
        headers.add("content-type", "application/json");
        headers.add("CB-ACCESS-KEY", publicKey);
        headers.add("CB-ACCESS-SIGN", generateSignature(resource, method, body, timestamp));
        headers.add("CB-ACCESS-TIMESTAMP", timestamp);
        headers.add("CB-ACCESS-PASSPHRASE", passphrase);

        return new HttpEntity<String>(headers);
    }

    /**
     * The CB-ACCESS-SIGN header is generated by creating a sha256 HMAC using
     * the base64-decoded secret key on the prehash string for:
     * timestamp + method + requestPath + body (where + represents string concatenation)
     * and base64-encode the output.
     * The timestamp value is the same as the CB-ACCESS-TIMESTAMP header.
     * @param requestPath
     * @param method
     * @param body
     * @param timestamp
     * @return
     */
    @Override
    public String generateSignature(String requestPath, String method, String body, String timestamp) {
        try {
            String prehash = timestamp + method.toUpperCase() + requestPath + body;
            byte[] secretDecoded = Base64.getDecoder().decode(secretKey);
            SecretKeySpec keyspec = new SecretKeySpec(secretDecoded, "HmacSHA256");
            Mac sha256 = (Mac) GdaxConstants.SHARED_MAC.clone();
            sha256.init(keyspec);
            return Base64.getEncoder().encodeToString(sha256.doFinal(prehash.getBytes()));
        } catch (CloneNotSupportedException | InvalidKeyException e) {
            e.printStackTrace();
            throw new RuntimeErrorException(new Error("Cannot set up authentication headers."));
        }
    }
}
