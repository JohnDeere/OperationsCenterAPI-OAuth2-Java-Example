# Welcome to the My John Deere OAuth2 Java Example.

This project gives a full native java example of:
   * Getting a OAuth2 access token via OIDC (OpenId Connect)
   * Using a refresh token to renew your own
   * Call the MyJohnDeere APIs with your access token
   
## Requirements
* Java 8+
* A free port 9090 (you can change this)
   
## How to start this project
* Clone this repository:
    * ```git clone git@github.com:JohnDeere/MyJohnDeereAPI-OAuth2-Java-Example.git```
* Build the project
    * ```mvn package```
* Start it
    * ```java -jar target/oauth2-example-1.0.jar```
    * you may also use the ```start.sh``` shell script.
* Open a browser and go to http://localhost:9090

## Using this project
* Once in the browser you will need a few things
   * A ClientId and Secret from your application on https://developer.deere.com 
   * The callback for your application needs to be configured for the URL of this app. You have a few choices here:
      * In  developer.deere.com you can add http://localhost:9090/callback as one of the callbacks  (you can have more than one). It is NOT recommended to keep this around for production use.
      * You can make some some other URL and register it in your /etc/hosts files. This also needs to be registered in developer.deere.com
      * Please allow up to 20 minutes for any changes to be replicated.
