/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.transport;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.android.SdkConstants;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.IDevice;
import com.android.ddmlib.IShellOutputReceiver;
import com.android.sdklib.AndroidVersion;
import com.android.tools.profiler.proto.Common;
import com.android.tools.profiler.proto.Transport;
import com.android.tools.profiler.proto.Transport.TimeRequest;
import com.android.tools.profiler.proto.Transport.TimeResponse;
import com.android.tools.profiler.proto.TransportServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.MethodDescriptor;
import io.grpc.ServerServiceDefinition;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.internal.ServerImpl;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

public class TransportServiceProxyTest {
  @Test
  public void testBindServiceContainsAllMethods() throws Exception {
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, new Client[0]);
    Common.Device transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);
    TransportServiceProxy proxy =
      new TransportServiceProxy(mockDevice, transportMockDevice,
                                startNamedChannel("testBindServiceContainsAllMethods", new FakeTransportService()));

    ServerServiceDefinition serverDefinition = proxy.getServiceDefinition();
    Collection<MethodDescriptor<?, ?>> allMethods = TransportServiceGrpc.getServiceDescriptor().getMethods();
    Set<MethodDescriptor<?, ?>> definedMethods =
      serverDefinition.getMethods().stream().map(method -> method.getMethodDescriptor()).collect(Collectors.toSet());
    assertThat(definedMethods.size()).isEqualTo(allMethods.size());
    definedMethods.containsAll(allMethods);
  }

  @Test
  public void testUnknownDeviceLabel() throws Exception {
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, new Client[0]);
    Common.Device profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);
    assertThat(profilerDevice.getModel()).isEqualTo("Unknown");
  }

  @Test
  public void testUnknownEmulatorLabel() throws Exception {
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.BASE, new Client[0]);
    when(mockDevice.isEmulator()).thenReturn(true);
    when(mockDevice.getAvdName()).thenReturn(null);
    Common.Device profilerDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);

    assertThat(profilerDevice.getModel()).isEqualTo("Unknown");
  }

  @Test
  public void testClientsWithNullDescriptionsNotCached() throws Exception {
    Client client1 = createMockClient(1, "test1", "testClientDescription");
    Client client2 = createMockClient(2, "test2", null);
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, new Client[]{client1, client2});
    Common.Device transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);

    TransportServiceProxy proxy =
      new TransportServiceProxy(mockDevice, transportMockDevice,
                                startNamedChannel("testClientsWithNullDescriptionsNotCached", new FakeTransportService()));
    Map<Client, Common.Process> cachedProcesses = proxy.getCachedProcesses();
    assertThat(cachedProcesses.size()).isEqualTo(1);
    Map.Entry<Client, Common.Process> cachedProcess = cachedProcesses.entrySet().iterator().next();
    assertThat(cachedProcess.getKey()).isEqualTo(client1);
    assertThat(cachedProcess.getValue().getPid()).isEqualTo(1);
    assertThat(cachedProcess.getValue().getName()).isEqualTo("testClientDescription");
    assertThat(cachedProcess.getValue().getState()).isEqualTo(Common.Process.State.ALIVE);
    assertThat(cachedProcess.getValue().getAbiCpuArch()).isEqualTo(SdkConstants.CPU_ARCH_ARM);
  }

  @Test
  public void testEventStreaming() throws Exception {
    Client client1 = createMockClient(1, "test1", "testClient1");
    Client client2 = createMockClient(2, "test2", "testClient2");
    IDevice mockDevice = createMockDevice(AndroidVersion.VersionCodes.O, new Client[]{client1, client2});
    Common.Device transportMockDevice = TransportServiceProxy.transportDeviceFromIDevice(mockDevice);

    FakeTransportService thruService = new FakeTransportService();
    ManagedChannel thruChannel = startNamedChannel("testEventStreaming", thruService);
    TransportServiceProxy proxy =
      new TransportServiceProxy(mockDevice, transportMockDevice, thruChannel);
    List<Common.Event> receivedEvents = new ArrayList<>();
    // We should expect six events: two process starts events, followed by event1 and event2, then process ends events.
    CountDownLatch latch = new CountDownLatch(6);
    proxy.getEvents(Transport.GetEventsRequest.getDefaultInstance(), new StreamObserver<Common.Event>() {
      @Override
      public void onNext(Common.Event event) {
        receivedEvents.add(event);
        latch.countDown();
      }

      @Override
      public void onError(Throwable throwable) {
        assert false;
      }

      @Override
      public void onCompleted() {
      }
    });

    Common.Event event1 = Common.Event.newBuilder().setPid(1).setIsEnded(true).build();
    Common.Event event2 = Common.Event.newBuilder().setPid(2).setIsEnded(true).build();
    thruService.addEvents(event1, event2);
    thruService.stopEventThread();
    thruChannel.shutdownNow();
    proxy.disconnect();
    latch.await();

    assertThat(receivedEvents).hasSize(6);
    // We know event 1 and event 2 will arrive in order. But the two processes' events can arrive out of order. So here we only check
    // whether those events are somewhere in the returned list.
    assertThat(receivedEvents.stream().filter(e -> e.getProcess().getProcessStarted().getProcess().getPid() == 1).count()).isEqualTo(1);
    assertThat(receivedEvents.stream().filter(e -> e.getProcess().getProcessStarted().getProcess().getPid() == 2).count()).isEqualTo(1);
    assertThat(receivedEvents.get(2)).isEqualTo(event1);
    assertThat(receivedEvents.get(3)).isEqualTo(event2);
    assertThat(
      receivedEvents.stream().filter(e -> e.getKind() == Common.Event.Kind.PROCESS && e.getGroupId() == 1 && e.getIsEnded()).count())
      .isEqualTo(1);
    assertThat(
      receivedEvents.stream().filter(e -> e.getKind() == Common.Event.Kind.PROCESS && e.getGroupId() == 2 && e.getIsEnded()).count())
      .isEqualTo(1);
  }

  /**
   * @param uniqueName Name should be unique across tests.
   */
  private ManagedChannel startNamedChannel(String uniqueName, FakeTransportService thruService) throws IOException {
    InProcessServerBuilder builder = InProcessServerBuilder.forName(uniqueName);
    builder.addService(thruService);
    ServerImpl server = builder.build();
    server.start();

    return InProcessChannelBuilder.forName(uniqueName).build();
  }

  @NotNull
  private IDevice createMockDevice(int version, @NotNull Client[] clients) throws Exception {
    IDevice mockDevice = mock(IDevice.class);
    when(mockDevice.getSerialNumber()).thenReturn("Serial");
    when(mockDevice.getName()).thenReturn("Device");
    when(mockDevice.getVersion()).thenReturn(new AndroidVersion(version, "API"));
    when(mockDevice.isOnline()).thenReturn(true);
    when(mockDevice.getClients()).thenReturn(clients);
    when(mockDevice.getState()).thenReturn(IDevice.DeviceState.ONLINE);
    when(mockDevice.getAbis()).thenReturn(Collections.singletonList("armeabi"));
    doAnswer(new Answer<Void>() {
      @Override
      public Void answer(InvocationOnMock invocation) {
        Object[] args = invocation.getArguments();
        ((IShellOutputReceiver)args[1]).addOutput("boot-id\n".getBytes(), 0, 8);
        return null;
      }
    }).when(mockDevice).executeShellCommand(anyString(), any(IShellOutputReceiver.class));
    return mockDevice;
  }

  @NotNull
  private Client createMockClient(int pid, @Nullable String packageName, @Nullable String clientDescription) {
    ClientData mockData = mock(ClientData.class);
    when(mockData.getPid()).thenReturn(pid);
    when(mockData.getPackageName()).thenReturn(packageName);
    when(mockData.getClientDescription()).thenReturn(clientDescription);

    Client mockClient = mock(Client.class);
    when(mockClient.getClientData()).thenReturn(mockData);
    return mockClient;
  }

  private static class FakeTransportService extends TransportServiceGrpc.TransportServiceImplBase {
    final LinkedBlockingDeque<Common.Event> myEventQueue = new LinkedBlockingDeque<>();
    @Nullable private Thread myEventThread;

    @Override
    public void getCurrentTime(TimeRequest request, StreamObserver<TimeResponse> responseObserver) {
      responseObserver.onNext(TimeResponse.getDefaultInstance());
      responseObserver.onCompleted();
    }

    @Override
    public void getEvents(Transport.GetEventsRequest request, StreamObserver<Common.Event> responseObserver) {
      myEventThread = new Thread(() -> {
        while (!Thread.currentThread().isInterrupted() || !myEventQueue.isEmpty()) {
          try {
            Common.Event event = myEventQueue.take();
            if (event != null) {
              responseObserver.onNext(event);
            }
          }
          catch (InterruptedException exception) {
          }
        }
        responseObserver.onCompleted();
      });
      myEventThread.start();
    }

    void addEvents(@NotNull Common.Event... events) {
      for (Common.Event event : events) {
        myEventQueue.offer(event);
      }
      while (!myEventQueue.isEmpty()) {
        try {
          // Wait until all events have been sent through.
          Thread.sleep(10);
        }
        catch (InterruptedException exception) {
        }
      }
    }

    void stopEventThread() {
      if (myEventThread != null) {
        myEventThread.interrupt();
      }
    }
  }
}
