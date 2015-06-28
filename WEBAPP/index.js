var sock = new SockJS('http://localhost/chimp/webSocketHandler');
 sock.onopen = function() {
     console.log('open');
 };
 sock.onmessage = function(e) {
     console.log('message', e.data);
 };
 sock.onclose = function() {
     console.log('close');
 };

 sock.send('test');
 sock.close();