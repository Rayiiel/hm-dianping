--判断库存是否充足需要传入参数 优惠券id
--判断该用户是否下单需要传入参数 用户id
local voucherId = ARGV[1]
local userId = ARGV[2]
local orderId = ARGV[3]
--1.库存和订单在redis中的key分别是什么
local stockKey="seckill:stock:".. voucherId
local orderKey="seckill:order:"..voucherId

--2.判断库存是否充足
local stockCount = redis.call('get', stockKey)
if  stockCount == false or stockCount == nil  then
   redis.log(redis.LOG_NOTICE, "Stock key does not exist: " .. stockKey)
   return 11  -- 库存不足
elseif tonumber(stockCount) <= 0 then
   redis.log(redis.LOG_NOTICE, "Insufficient stock for key: " .. stockKey)
   return 12  -- 库存不足
end
--3.判断该用户是否已经下过单了
if(redis.call('sismember',orderKey,userId)==1) then
    --已经下过单的，返回2
    return 2
end
--表示可以下单了
--4.减库存
redis.call('incrby',stockKey,-1)
--5.添加用户id到集合中
redis.call('sadd',orderKey,userId)
--6.发送消息到队列中，XADD stream.orders * k1 v1 k2 v2
redis.call('xadd','stream.orders','*','userId',userId,'voucherId',voucherId,'id',orderId)
return 0