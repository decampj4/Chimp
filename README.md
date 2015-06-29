#Chimp

Build Instructions
------------------
### Chimp (API) - Individual WebSocket Servers
Locally:
   gradle clean build (I use gradle clean deploy, but the paths are currently hardcoded to my machine, so I would avoid using that command)

Container:
   Rely on Dockerfile

Run the created jar in a Tomcat

### Caesar (API_CONTROLLER) - Controller that coordinates between the various websocket servers
Locally:
   gradle clean fatJar

Container:
   Rely on Dockerfile

Run:
    java -cp build/libs/caesar.jar com.caesar.main.TCPServer

### AngularJS App (WEBAPP)
Locally:
    Copy the files in the WEBAPP/ folder into a folder in your {TOMCAT_HOME}/webapps/ directory and then you can access this app just like any other webapp

Container:
    Will eventually containerize this
