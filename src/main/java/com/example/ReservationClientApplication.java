package com.example;

import java.util.Collection;
import java.util.Collections;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.circuitbreaker.EnableCircuitBreaker;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.netflix.zuul.EnableZuulProxy;
import org.springframework.cloud.sleuth.sampler.AlwaysSampler;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.annotation.Output;
import org.springframework.cloud.stream.messaging.Source;
import org.springframework.context.annotation.Bean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import com.netflix.hystrix.contrib.javanica.annotation.HystrixCommand;

@EnableZuulProxy
@EnableBinding(Source.class)
@EnableCircuitBreaker
@EnableDiscoveryClient
@SpringBootApplication
public class ReservationClientApplication {
	
	@Bean
	AlwaysSampler alwaysSampler(){
		return new AlwaysSampler();
	}

	public static void main(String[] args) {
		SpringApplication.run(ReservationClientApplication.class, args);
	}
}

@RestController
@RequestMapping("/reservations")
class ReservationApiGatewayRestController{

	@Autowired
	@LoadBalanced
	private RestTemplate restTemplate;
	
	@Autowired
	@Output(Source.OUTPUT)
	private MessageChannel messageChannel;
	
	@RequestMapping( method = RequestMethod.POST)
	public void write(@RequestBody Reservation r){
		this.messageChannel.send(MessageBuilder.withPayload(r.getReservationName()).build());
	}
	
	public Collection<String> getReservationNamesFallback(){
		return Collections.emptyList();
	}
	
	@HystrixCommand(fallbackMethod = "getReservationNamesFallback")
	@RequestMapping("names")
	public Collection<String> getReservationNames(){

		ParameterizedTypeReference<Resources<Reservation>> ptr = 
				new ParameterizedTypeReference<Resources<Reservation>>(){};

				ResponseEntity<Resources<Reservation>> responseEntity = 
						this.restTemplate.exchange("http://reservation-service/reservations", 
								HttpMethod.GET, null, ptr);
				
				Collection<String> nameList = responseEntity
						.getBody()
						.getContent()
						.stream()
						.map(Reservation::getReservationName)
						.collect(Collectors.toList());

				return nameList;
	}
}

class Reservation{
	private Long id;
	private String reservationName;
	public Reservation(){}
	public Long getId() {
		return id;
	}
	public String getReservationName() {
		return reservationName;
	}
	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder("Reservation{");
		sb.append("id=").append(id);
		sb.append(", reservationName='").append(reservationName).append("'}");
		return sb.toString();
	}
}