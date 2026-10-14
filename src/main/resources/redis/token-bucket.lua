local clock = redis.call('TIME')
local now = tonumber(clock[1]) + tonumber(clock[2]) / 1000000
local states = {}
local retry = 0
for i,key in ipairs(KEYS) do
 local capacity=tonumber(ARGV[(i-1)*2+1])
 local rate=tonumber(ARGV[(i-1)*2+2])
 local old=redis.call('HMGET',key,'tokens','updated')
 local previous=tonumber(old[2]) or now
 local tokens=math.min(capacity,(tonumber(old[1]) or capacity)+math.max(0,now-previous)*rate)
 states[i]={tokens=tokens,capacity=capacity,rate=rate}
 if tokens < 1 then retry=math.max(retry,math.ceil((1-tokens)/rate)) end
end
for i,key in ipairs(KEYS) do
 local state=states[i]
 local tokens=state.tokens
 if retry==0 then tokens=tokens-1 end
 redis.call('HSET',key,'tokens',tostring(tokens),'updated',tostring(now))
 redis.call('PEXPIRE',key,math.max(1000,math.ceil(state.capacity/state.rate*2000)))
end
if retry==0 then return {1,0} else return {0,retry} end
