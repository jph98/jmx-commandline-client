package com.chester;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServerConnection;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectInstance;
import javax.management.ObjectName;
import javax.management.ReflectionException;
import javax.management.remote.JMXConnector;
import javax.management.remote.JMXConnectorFactory;
import javax.management.remote.JMXServiceURL;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;

/**
 * Main JMX command line client.
 */
public class JMXCLI {

    @Argument(required = true, index = 0, usage = "Action to perform [list|get]")
    private String action;
    
    @Argument(required = true, index = 1, usage = "hostname:port of the jmx server, e.g. localhost:8090")
    private String hostPort;

    @Argument(required = true, index = 2, usage = "username of the secured JMX Server")
    private String username;
    
    @Argument(required = true, index = 3, usage = "password of the secured JMX Server") 
    private String password;
    
    @Argument(required = false, index = 4, usage = "Name of the JMX object, e.g. com.mchange.v2.c3p0:type=PooledDataSource.* will return the first matching object")
    private String objectName;

    @Argument(required = false, index = 5, usage = "Attribute name of the JMX object, e.g. numBusyConnections")
    private String attributeName;
    
    @Argument(required = false, index = 6, usage = "Whether to loop infinitely [true|false]")
    private Boolean infinite = false;
    
    @Argument(required = false, index = 7, usage = "Time to pause between runs in seconds]")
    private long pause;

    private JMXConnector jmxConnector;

    private boolean printheader = false;

    /**
     * Connect to the JMXServer
     * @return connector
     */
    private JMXConnector connect() {

        String[] hostAndPort = hostPort.split(":");
        if (hostAndPort.length != 2) throw new IllegalStateException("Could not parse hostname and port from " + hostPort);
        String parsedHost = hostAndPort[0];
        String parsedPort = hostAndPort[1];
               
        if (parsedHost == null || parsedHost == "") throw new IllegalArgumentException("Could not parse host");
        if (parsedPort == null || parsedPort == "") throw new IllegalArgumentException("Could not parse host");        

        try {

            if (jmxConnector == null) {               

                JMXServiceURL serviceURL = new JMXServiceURL("service:jmx:rmi:///jndi/rmi://" + parsedHost + ":" + parsedPort + "/jmxrmi");

                HashMap<String, String[]> credentials = new HashMap<String, String[]>();
                credentials.put(JMXConnector.CREDENTIALS, new String[] { this.username, this.password });
                                               
                if (infinite && printheader) {
                    System.out.println("Created connection with service URL: " + serviceURL);
                    System.out.println("Will execute until CTRL-C received");
                }

                jmxConnector = JMXConnectorFactory.connect(serviceURL, credentials);

            } else {
                return jmxConnector;
            }

        } catch (IOException e) {
            System.err.println("Could not connect via JMX " + parsedHost + ":" + parsedPort + "\n" + e);
        }
        return null;
    }   

    public void printObjectNames() {
        for (String name : getObjectNameList()) {
            System.out.println(name);
        }
    }

    public List<String> getObjectNameList() {
        List<String> names = new ArrayList<String>();        
        Set<ObjectInstance> beans;
        try {
            beans = jmxConnector.getMBeanServerConnection().queryMBeans(null, null);

            for (ObjectInstance instance : beans) {
                names.add(instance.getObjectName().toString());
            }
        } catch (IOException e) {
            System.err.println("IOExcepton " + e);
        } 
        return names;
    }

    public String nameExists(String name) {
        
        ObjectName oName = null;
        if (name != null) {
            oName = createJmxObject(name);
        }

        Set<ObjectInstance> beans;
        try {
            beans = jmxConnector.getMBeanServerConnection().queryMBeans(oName, null);

            for (ObjectInstance instance : beans) {
                return instance.getObjectName().toString();
            }

        } catch (IOException e) {
            System.err.println("IOExcepton " + e);
        } 

        return null;
    }

    private void closeConnection() {
        try {
            if (jmxConnector != null) {
                jmxConnector.close();
            }
        } catch (IOException e) {
            try {
                System.out.println("Could not close connection " + jmxConnector.getConnectionId());
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }
    }

    public void printAttributes(String name) {
        for (String attribute : getAttributeList(name)) {
            System.out.println(attribute);
        }
    }

    public List<String> getAttributeList(String name) {
        List<String> attributes = new ArrayList<String>();
        ObjectName oName = null;
        if (name != null) {
            oName = createJmxObject(name);
        }

        try {
            MBeanInfo info = jmxConnector.getMBeanServerConnection().getMBeanInfo(oName);
            for (MBeanAttributeInfo att : info.getAttributes()) {
                attributes.add(" - " + att.getName() + " [" + att.getType() + "] " + att.getDescription());
            }

        } catch (ReflectionException e) {
            System.err.println("ReflectionException " + e);
        } catch (IOException e) {
            System.err.println("IOExcepton " + e);
        } catch (InstanceNotFoundException e) {
            System.err.println("InstanceNotFoundException " + e);
        } catch (IntrospectionException e) {
            System.err.println("IntrospectionException " + e);
        } 
        return attributes;
    }

    public String getAttribute(String name, String attribute) {

        if (jmxConnector != null) {

            try {

                ObjectName obj = createJmxObject(name);
                MBeanServerConnection connection = jmxConnector.getMBeanServerConnection();

                return connection.getAttribute(obj, attribute).toString();

            } catch (InstanceNotFoundException e) {
                System.err.println("InstanceNotFoundException " + e);
            } catch (ReflectionException e) {
                System.err.println("ReflectionException " + e);
            } catch (IOException e) {
                System.err.println("IOException " + e);
            } catch (AttributeNotFoundException e) {
                System.err.println("AttributeNotFoundException " + e);
            } catch (MBeanException e) {
                System.err.println("MBeanException " + e);
            } 
        } else {
            return "Could not create connection to host";
        }

        return "Not supported yet";
    }

    public ObjectName createJmxObject(String aName) {
        ObjectName oName = null;
        try {
            oName = new ObjectName(aName);
        } catch (MalformedObjectNameException e) {
            System.err.println("MalformedObjectNameException " + e);
        }
        return oName;
    }

    private String getObjectName() {
        return objectName;
    }
    
    private String getAttributeName() {        
        return attributeName;
    }
    
    private boolean getInfinite() {
        return infinite;
    }

    private String findObject(String objectNameToFind) {

        List<String> objectNames = getObjectNameList();
        for (String name : objectNames) {
            if (name.matches(objectNameToFind)) {
                return name;
            }
        }
        return "";
    }
    
    private String getAction() {
        return action;
    }

    private long getPause() {        
        return pause * 1000;
    }     

    public static void main(String[] args) {

        JMXCLI client = new JMXCLI();
        CmdLineParser parser = new CmdLineParser(client);
        parser.setUsageWidth(80); 

        try {
            parser.parseArgument(args);            
            String action = client.getAction();                        
            client.connect();

            if (action.equals("get")) {
                
                getObject(client);
                
            } else if (action.equals("attr")) {
                
                listAttributes(client);
                
            } else if (action.equals("list")) {
                
                listObjects(client);
                
            }
            
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            System.err.println(" [options...] arguments...");
            parser.printUsage(System.err);
            System.err.println();
            System.exit(1);
        } finally {
            client.closeConnection();
        }
    }

    private static void listObjects(JMXCLI client) {
        
        System.out.println("Listing JMX objects for client");        
        for (String objectName: client.getObjectNameList()) {            
            System.out.println(String.format("Found object: %s", objectName));
        }
    }

    private static void listAttributes(JMXCLI client) {
        
        System.out.println(String.format("JMX Attributes for %s", client.getObjectName()));
        if (client.getObjectName() != null) {
            for (String attributeName: client.getAttributeList(client.getObjectName())) {                
                System.out.println(String.format("Found JMX attribute: %s", attributeName));
            }
        } else {
            System.out.println("You must specify an JMX objectname");
        }
    }

    private static void getObject(JMXCLI client) {
                
        String objectName = client.getObjectName();
        if (objectName.contains("*")) {
            objectName = client.findObject(objectName);
        }               
   
        if (client.getInfinite()) {
            
            while (true) {
                String attribute = client.getAttributeName();
                System.out.println(String.format("%s", client.getAttribute(objectName, attribute)));
                try {
                    Thread.sleep(client.getPause());
                } catch (InterruptedException e) {                        
                }
            }                   
        } else {
            String attribute = client.getAttributeName();
            System.out.println(String.format("%s", client.getAttribute(objectName, attribute)));
        }
    }   
}
