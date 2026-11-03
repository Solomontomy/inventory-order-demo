$baseUrl = 'http://127.0.0.1:8080/api'
$products = Invoke-RestMethod "$baseUrl/products"
$products | Format-Table
$payload = @{reference=('DEMO-' + [guid]::NewGuid().ToString()); lines=@(@{productId=$products[0].id; quantity=2})} | ConvertTo-Json -Depth 4
$order = Invoke-RestMethod "$baseUrl/orders" -Method Post -ContentType 'application/json' -Body $payload
$order | ConvertTo-Json -Depth 5
Invoke-RestMethod "$baseUrl/orders/$($order.id)/cancel" -Method Post | ConvertTo-Json -Depth 5
