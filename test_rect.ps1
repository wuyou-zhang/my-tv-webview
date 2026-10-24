$ws = New-Object System.Net.WebSockets.ClientWebSocket
$cts = New-Object System.Threading.CancellationTokenSource
$uri = New-Object System.Uri("ws://localhost:9222/devtools/page/AA155CE16A0A31DEC4836ED385167A42")
$ws.ConnectAsync($uri, $cts.Token).Wait()

$jsExpression = @"
(function() {
    var v = document.querySelector('video');
    var r = v.getBoundingClientRect();
    return {
        top: r.top, left: r.left, width: r.width, height: r.height,
        winW: window.innerWidth, winH: window.innerHeight,
        parent: v.parentElement.className
    };
})()
"@

$reqObj = @{
    id = 6
    method = "Runtime.evaluate"
    params = @{
        expression = $jsExpression
        returnByValue = $true
    }
}
$json = $reqObj | ConvertTo-Json -Compress
$bytes = [System.Text.Encoding]::UTF8.GetBytes($json)
$segment = New-Object System.ArraySegment[byte] -ArgumentList @($bytes, 0, $bytes.Length)
$ws.SendAsync($segment, [System.Net.WebSockets.WebSocketMessageType]::Text, $true, $cts.Token).Wait()

$buffer = New-Object byte[] 65536
$recvSegment = New-Object System.ArraySegment[byte] -ArgumentList @($buffer, 0, $buffer.Length)
$result = $ws.ReceiveAsync($recvSegment, $cts.Token).Result
$resStr = [System.Text.Encoding]::UTF8.GetString($buffer, 0, $result.Count)
$resStr
$ws.CloseAsync([System.Net.WebSockets.WebSocketCloseStatus]::NormalClosure, "done", $cts.Token).Wait()
