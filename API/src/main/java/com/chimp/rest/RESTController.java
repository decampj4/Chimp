package com.chimp.rest;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.chimp.constants.Constants;

@RestController
public class RESTController {
	
	@RequestMapping(Constants.REST_HELLO_WORLD)
	public String helloWorld(){
		return "Hello World!";
	}
}
