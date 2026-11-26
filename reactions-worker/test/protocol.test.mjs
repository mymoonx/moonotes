import test from 'node:test';
import assert from 'node:assert/strict';
import { isRoomKey, validPlayer, validEmote, parseFrame, rateAllowed, LIMITS }
  from '../src/protocol.mjs';

const user='123e4567-e89b-12d3-a456-426614174000';
const id='01J3QY00000000000000000000';

test('only unguessable 64-hex room capabilities', () => {
  assert.equal(isRoomKey('f'.repeat(64)), true);
  assert.equal(isRoomKey('f'.repeat(63)), false);
  assert.equal(isRoomKey('/v1/room/' + 'f'.repeat(64)), false);
  assert.equal(isRoomKey('F'.repeat(64)), false);
});

test('join accepts Minecraft player identity shape, not trusted identity', () => {
  assert.equal(validPlayer(user,'Player_1'), true);
  assert.equal(validPlayer('not-a-uuid','Player_1'), false);
  assert.equal(validPlayer(user,'bad name'), false);
  assert.deepEqual(parseFrame(JSON.stringify({type:'join',uuid:user,name:'Player_1'})),
    {type:'join',uuid:user,name:'Player_1'});
});

test('reaction only transfers exact bounded emote name and ID', () => {
  assert.equal(validEmote(id,'catJAM'), true);
  assert.equal(validEmote(id,'😺'), true);
  assert.equal(validEmote(id,'bad name'), false);
  assert.deepEqual(parseFrame(JSON.stringify({type:'reaction',id,name:'catJAM',player:'Impersonation'})),
    {type:'reaction',id:id.toLowerCase(),name:'catJAM'});
  assert.equal(parseFrame(JSON.stringify({type:'chat',text:'PRIVATE'})),null);
  assert.equal(parseFrame(JSON.stringify({type:'reaction',id,name:'\u00a7invalid'})),null);
  assert.equal(parseFrame('x'.repeat(LIMITS.MAX_MSG_BYTES+1)),null);
});

test('per-connection reaction rate is bounded and resets', () => {
  const state={lastAt:0,windowStart:0,count:0};
  assert.equal(rateAllowed(state,10_000),true);
  assert.equal(rateAllowed(state,10_400),false);
  assert.equal(rateAllowed(state,10_801),true);
  for(let i=2;i<8;i++) assert.equal(rateAllowed(state,10_801+i*801),true);
  assert.equal(rateAllowed(state,17_500),false);
  assert.equal(rateAllowed(state,21_000),true);
});
