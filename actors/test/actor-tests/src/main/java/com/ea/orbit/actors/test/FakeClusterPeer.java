/*
Copyright (C) 2015 Electronic Arts Inc.  All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions
are met:

1.  Redistributions of source code must retain the above copyright
    notice, this list of conditions and the following disclaimer.
2.  Redistributions in binary form must reproduce the above copyright
    notice, this list of conditions and the following disclaimer in the
    documentation and/or other materials provided with the distribution.
3.  Neither the name of Electronic Arts, Inc. ("EA") nor the names of
    its contributors may be used to endorse or promote products derived
    from this software without specific prior written permission.

THIS SOFTWARE IS PROVIDED BY ELECTRONIC ARTS AND ITS CONTRIBUTORS "AS IS" AND ANY
EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
DISCLAIMED. IN NO EVENT SHALL ELECTRONIC ARTS OR ITS CONTRIBUTORS BE LIABLE FOR ANY
DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
(INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
(INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF
THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.ea.orbit.actors.test;

import com.ea.orbit.actors.cluster.IClusterPeer;
import com.ea.orbit.actors.cluster.INodeAddress;
import com.ea.orbit.actors.cluster.MessageListener;
import com.ea.orbit.actors.cluster.ViewListener;
import com.ea.orbit.concurrent.ExecutorUtils;
import com.ea.orbit.concurrent.Task;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Fake group networking peer used during unit tests.
 * <p>
 * Using this peer is considerably faster than using jgroups during the tests.
 * </p>
 * It's recommended to use the fake network for application unit tests.
 */
public class FakeClusterPeer implements IClusterPeer
{
    private ViewListener viewListener;
    private MessageListener messageListener;
    private FakeGroup group;
    private INodeAddress address;
    private AtomicLong messagesSent = new AtomicLong();
    private AtomicLong messagesSentOk = new AtomicLong();
    private AtomicLong messagesReceived = new AtomicLong();
    private AtomicLong messagesReceivedOk = new AtomicLong();
    private static Executor pool = ExecutorUtils.newScalingThreadPool(5, 10, 5, TimeUnit.SECONDS, 1000);
    private Task startFuture = new Task();

    public FakeClusterPeer()
    {
    }

    public Task join(String clusterName)
    {
        group = FakeGroup.get(clusterName);
        return Task.fromFuture(CompletableFuture.runAsync(() -> {
            address = group.join(this);
            startFuture.complete(null);
        }, pool));
    }

    @Override
    public void leave()
    {
        group.leave(this);
    }

    public void onViewChanged(final List<INodeAddress> newView)
    {
        viewListener.onViewChange(newView);
    }

    public void onMessageReceived(final INodeAddress from, final byte[] buff)
    {
        messagesReceived.incrementAndGet();
        messageListener.receive(from, buff);
        messagesReceivedOk.incrementAndGet();
    }

    @Override
    public INodeAddress localAddress()
    {
        return address;
    }

    @Override
    public void registerViewListener(final ViewListener viewListener)
    {
        this.viewListener = viewListener;
    }

    @Override
    public void registerMessageReceiver(final MessageListener messageListener)
    {
        this.messageListener = messageListener;
    }

    @Override
    public void sendMessage(final INodeAddress to, final byte[] message)
    {
        startFuture.join();
        messagesSent.incrementAndGet();
        group.sendMessage(address, to, message);
        messagesSentOk.incrementAndGet();
    }

    @Override
    public <K, V> ConcurrentMap<K, V> getCache(final String name)
    {
        return group.getCache(name);
    }

    void setAddress(final INodeAddress address)
    {
        this.address = address;
    }
}
