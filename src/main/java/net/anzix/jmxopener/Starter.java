package net.anzix.jmxopener;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class Starter {
  private static final Logger logger = Logger.getLogger(Starter.class.getCanonicalName());

  public static void main(String[] args) {
    new Starter().attach(args[0], Integer.parseInt(args[1]));
  }

  private void attach(String pattern, int port) {
    String myPid = getMyPid();
    List<VirtualMachineDescriptor> vms = VirtualMachine.list();

    List<VirtualMachineDescriptor> selectedVms = vms.stream()
        .filter(descriptor -> !myPid.equals(descriptor.id()))
        .filter(descriptor -> descriptor.displayName().contains(pattern) || descriptor.id().equals(pattern))
        .collect(Collectors.toList());
    if (selectedVms.size() > 1) {
      System.out.println("Too many jvm are selected. There should be exactly one match");
      for (VirtualMachineDescriptor vm : selectedVms) {
        System.out.println(vm.id() + " " + vm.displayName());
      }
    } else if (selectedVms.size() == 0) {
      System.out.println("No matching jvm. Running jvms:");
      for (VirtualMachineDescriptor vm : vms) {
        System.out.println(vm.id() + " " + vm.displayName());
      }
    } else {
      openJmxPort(selectedVms.get(0), port);
    }

  }

  private String getMyPid() {
    return ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
  }

  private void openJmxPort(VirtualMachineDescriptor virtualMachineDescriptor, int port) {
    VirtualMachine virtualMachine = attach(virtualMachineDescriptor);
    try {

      final String remoteConnectorPort = virtualMachine.getSystemProperties().getProperty("com.sun.management.jmxremote.port");
      if (remoteConnectorPort != null) {
        final String remoteConnectorAddress = "service:jmx:rmi:///jndi/rmi://" + InetAddress.getLocalHost().getHostName() + ":" + remoteConnectorPort + "/jmxrmi";
        System.out.println("JMX agent has already been started: " + remoteConnectorAddress != null ? "    remote jmx (" + remoteConnectorAddress + ")" : "");
        return;
      }

      Properties props = virtualMachine.getAgentProperties();
      if (props.getProperty("com.sun.management.jmxremote.localConnectorAddress") == null) {
        loadMangementAgent(virtualMachine, port);
      } else {
        System.out.println("JMX agent has already been started.");
      }


    } catch (Exception e) {
      e.printStackTrace();
    } finally {
      detachSilently(virtualMachine);
    }


  }

  private VirtualMachine attach(VirtualMachineDescriptor virtualMachineDescriptor) {
    VirtualMachine virtualMachine = null;
    try {
      virtualMachine = VirtualMachine.attach(virtualMachineDescriptor);
    } catch (AttachNotSupportedException anse) {
      logger.log(Level.SEVERE, "Couldn't attach", anse);
    } catch (IOException ioe) {
      logger.log(Level.SEVERE, "Exception attaching or reading a jvm.", ioe);
    }
    return virtualMachine;
  }

  private String readSystemProperty(VirtualMachine virtualMachine, String propertyName) {
    String propertyValue = null;
    try {
      Properties systemProperties = virtualMachine.getSystemProperties();
      propertyValue = systemProperties.getProperty(propertyName);
    } catch (IOException e) {
      throw new RuntimeException("Reading system property failed", e);
    }
    return propertyValue;
  }

  private void detachSilently(VirtualMachine virtualMachine) {
    if (virtualMachine != null) {
      try {
        virtualMachine.detach();
      } catch (IOException e) {
        //
      }
    }

  }


  private void loadMangementAgent(VirtualMachine virtualMachine, int port) {
    final String id = virtualMachine.id();
    String agent = null;
    Boolean loaded = false;
    try {
      String javaHome = readSystemProperty(virtualMachine, "java.home");
      agent = javaHome + "/lib/management-agent.jar";
      virtualMachine.loadAgent(agent, "com.sun.management.jmxremote.authenticate=false,com.sun.management.jmxremote.ssl=false,com.sun.management.jmxremote.port=" + port);
      loaded = true;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private String readAgentProperty(VirtualMachine virtualMachine, String propertyName) {
    String propertyValue = null;
    try {
      Properties agentProperties = virtualMachine.getAgentProperties();
      propertyValue = agentProperties.getProperty(propertyName);
    } catch (IOException e) {
      throw new RuntimeException("Reading agent property failed", e);
    }
    return propertyValue;
  }
}
