wsApp.controller("wsAppController", function ($scope) {
  $scope.username = "";
  $scope.targetUsername = "";
  $scope.authenticated = false;
  $scope.wsMessages = [];

  $scope.sock = null;

  $scope.beginChat = function () {
    $scope.sock = new SockJS('http://localhost:8080/chimp/websocket');
    $scope.sock.onopen = function () {
      console.log('open');
      $scope.sock.send("{'username': '" + $scope.username + "'}");
    };
    $scope.sock.onmessage = function (e) {
      console.log('message', e.data);
      var data = e.data;
      var jsonObj = JSON.parse(data);
      if (jsonObj.authenticated) {
        $scope.authenticated = true;
        $scope.$apply();
      }
    };
    $scope.sock.onclose = function () {
      console.log('close');
    };
  };

  $scope.sendMessage = function () {
    $scope.wsMessages.push({"message" : $scope.messageToSend, "from": $scope.username, "to": $scope.targetUsername});
    $scope.sock.send("{'message': '" + $scope.messageToSend + "', 'to': '" + $scope.targetUsername + "'}");
  };

});