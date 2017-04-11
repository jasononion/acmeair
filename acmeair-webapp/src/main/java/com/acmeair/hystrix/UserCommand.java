package com.acmeair.hystrix;

import com.acmeair.entities.CustomerSession;
import com.acmeair.service.UserService;
import com.acmeair.web.RESTCookieSessionFilter;
import com.acmeair.web.dto.CustomerInfo;
import com.acmeair.web.dto.CustomerSessionInfo;
import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalancerClient;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.NoSuchElementException;


@Service
public class UserCommand implements UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserCommand.class);
    
    protected RestTemplate restTemplate = new RestTemplate();
    
    @Autowired
    private LoadBalancerClient loadBalancer;
    
    @Value("${customer.service.name:customerServiceApp}")
    private String customerServiceName;
    
    @HystrixCommand
    public CustomerInfo getCustomerInfo(String customerId) {
        ResponseEntity<CustomerInfo> resp = restTemplate.getForEntity(getCustomerServiceAddress() + "/rest/api/customer/{custid}", CustomerInfo.class, customerId);
        if (resp.getStatusCode() != HttpStatus.OK) {
            throw new NoSuchElementException("No such customer with id " + customerId);
        }
        return resp.getBody();
    }
    
    protected String getCustomerServiceAddress() {
        String address = loadBalancer.choose(customerServiceName).getUri().toString();
        logger.info("Just get the address {0} from LoadBalancer.", address);
        return address;
    }
    
    @HystrixCommand
    public CustomerSession validateCustomerSession(String sessionId) {
        ResponseEntity<CustomerSessionInfo> responseEntity = restTemplate.postForEntity(
                getCustomerServiceAddress() + "/rest/api/login/validate",
                validationRequest(sessionId),
                CustomerSessionInfo.class
        );
        return responseEntity.getBody();
    }
    
    private HttpEntity<MultiValueMap<String, String>> validationRequest(String sessionId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("sessionId", sessionId);
        
        return new HttpEntity<>(map, headers);
    }
}