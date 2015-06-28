#Chimp

Build Instructions
------------------
### Chimps - Individual WebSocket Servers
Locally:
   gradle clean deploy
Container:
   Rely on Dockerfile
Run in Tomcat

### Caesar - Controller that coordinates between the various websocket servers
Locally:
   gradle jar
Container:
   Rely on Dockerfile
Run:
    java -cp build/libs/API_CONTROLLER.jar com.caesar.main.TCPServer
