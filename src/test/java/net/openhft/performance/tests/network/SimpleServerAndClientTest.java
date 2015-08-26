package net.openhft.performance.tests.network;

import net.openhft.chronicle.bytes.Bytes;
import net.openhft.chronicle.network.AcceptorEventHandler;
import net.openhft.chronicle.network.TCPRegistry;
import net.openhft.chronicle.network.VanillaSessionDetails;
import net.openhft.chronicle.network.connection.TcpChannelHub;
import net.openhft.chronicle.network.jmh.WireEchoRequestHandler;
import net.openhft.chronicle.threads.EventGroup;
import net.openhft.chronicle.wire.TextWire;
import net.openhft.chronicle.wire.Wire;
import net.openhft.chronicle.wire.WireType;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.channels.SocketChannel;
import java.util.concurrent.TimeUnit;

import static net.openhft.chronicle.network.connection.SocketAddressSupplier.uri;

/**
 * Created by rob on 26/08/2015.
 */
public class SimpleServerAndClientTest {

    public void test() throws IOException {
        String desc = "host.port";
        TCPRegistry.createServerSocketChannelFor(desc);
        EventGroup eg = new EventGroup(true);
        eg.start();
        String expectedMessage = "<my message>";
        createServer(desc, eg);

        try (TcpChannelHub tcpChannelHub = createClient(eg, desc)) {

            // create the message to send§
            final long tid = tcpChannelHub.nextUniqueTransaction(System.currentTimeMillis());
            final Wire wire = new TextWire(Bytes.elasticByteBuffer());

            wire.writeDocument(true, w -> w.write(() -> "tid").int64(tid));
            wire.writeDocument(false, w -> w.write(() -> "payload").text(expectedMessage));

            // write the data to the socket
            tcpChannelHub.lock(() -> tcpChannelHub.writeSocket(wire));

            // read the reply from the socket ( timeout after 1 second )
            Wire reply = tcpChannelHub.proxyReply(TimeUnit.SECONDS.toMillis(1), tid);

            // read the reply and check the result
            reply.readDocument(null, data -> {
                final String text = data.read(() -> "payloadResponse").text();
            });

            eg.stop();
            TcpChannelHub.closeAllHubs();
            TCPRegistry.reset();
            tcpChannelHub.close();
        }
    }

    @NotNull
    private TcpChannelHub createClient(EventGroup eg, String desc) {
        return new TcpChannelHub(null, eg, WireType.TEXT, "", uri(desc), false);
    }

    private void createServer(String desc, EventGroup eg) throws IOException {
        AcceptorEventHandler eah = new AcceptorEventHandler(desc,
                () -> new WireEchoRequestHandler(WireType.TEXT), VanillaSessionDetails::new, 0, 0);
        eg.addHandler(eah);
        SocketChannel sc = TCPRegistry.createSocketChannel(desc);
        sc.configureBlocking(false);
    }

}