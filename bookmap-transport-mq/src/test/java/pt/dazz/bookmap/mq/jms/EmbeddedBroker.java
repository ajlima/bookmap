package pt.dazz.bookmap.mq.jms;

import java.util.Objects;
import java.util.Set;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.core.config.Configuration;
import org.apache.activemq.artemis.core.config.impl.ConfigurationImpl;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMAcceptorFactory;
import org.apache.activemq.artemis.core.security.CheckType;
import org.apache.activemq.artemis.core.security.Role;
import org.apache.activemq.artemis.core.server.embedded.EmbeddedActiveMQ;
import org.apache.activemq.artemis.jms.client.ActiveMQConnectionFactory;
import org.apache.activemq.artemis.spi.core.security.ActiveMQSecurityManager;

/**
 * An in-VM Artemis broker for the tests: no network, no persistence. A connection without a
 * user is accepted. The user {@code orders} must give the password {@code secret}. The broker
 * creates each queue when a client uses it.
 */
final class EmbeddedBroker implements AutoCloseable {

    static final String USER = "orders";
    static final String PASSWORD = "secret";

    private final EmbeddedActiveMQ server = new EmbeddedActiveMQ();

    EmbeddedBroker() throws Exception {
        Configuration config = new ConfigurationImpl()
            .setPersistenceEnabled(false)
            .setSecurityEnabled(true)
            .setJMXManagementEnabled(false)
            .addAcceptorConfiguration(new TransportConfiguration(InVMAcceptorFactory.class.getName()));
        server.setConfiguration(config);
        server.setSecurityManager(new TestSecurityManager());
        server.start();
    }

    ActiveMQConnectionFactory connectionFactory() {
        return new ActiveMQConnectionFactory("vm://0");
    }

    @Override
    public void close() throws Exception {
        server.stop();
    }

    private static final class TestSecurityManager implements ActiveMQSecurityManager {

        @Override
        public boolean validateUser(String user, String password) {
            return user == null || (USER.equals(user) && Objects.equals(PASSWORD, password));
        }

        @Override
        public boolean validateUserAndRole(String user, String password, Set<Role> roles,
                                           CheckType checkType) {
            return validateUser(user, password);
        }
    }
}
