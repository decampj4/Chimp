var sock = new SockJS('http://localhost:8080/chimp/websocket');
sock.onopen = function () {
   console.log('open');
   sock.send('test');
};
sock.onmessage = function (e) {
   console.log('message', e.data);
};
sock.onclose = function () {
   console.log('close');
};

