Tomcat Azure Session Manager
============================
A Java web application is deployed as a role on Windows Azure. One role may have multiple instances, 
and each instance can have a session for a client. Windows Azure's load balancer does not provide 
server affinity. In other words, is not guaranteed that every request of a client will be routed to 
the same role instance. Instead, the load balancer distributes traffic across all instances irrespective of 
the request origin. There can be no session sharing across the role instances because each role instance 
manages its own session. To share the session across multiple instances of a role, Atomus has implemented 
custom session manager for Tomcat. This manager stores the sessions in Windows Azure table storage.

The manager implementation provided by Atoms uses the Soyatec library to interact with 
the Windows Azure table storage to store and share sessions across multiple instances of a role. 
We forked the source code and contributed to the implementation to use the new Windows Azure SDK for Java 
instead of the Soyatec library to interact with table storage.

Setting up Tomcat Azure Session Manager:
========================================
Tomcat 6, 7 
------------
	1 Copy the tomcat-azure-session-manager[version].jar from the dist folder to [CATALINA_HOME]/lib
	2 Copy all of the jar files from lib/deploy folder to [CATALINA_HOME]/lib 
	3 Configure the following attributes in the Tomcat instance context.xml file, <Manager> element
	
	Attribute           Description
	className           must be set to uk.co.atomus.session.manager.AtomusManager
	accountKey          The azure storage account key
	accountName	    The azure storage account name
	tableName           The name of the table storage table to use for sessions 
				(will be created if does not exist)
	partitionKey        Corresponds to partitionKey in table storage
	
	Your Manager tag should end up looking something like this
	
	    <Manager className="uk.co.atomus.session.manager.AtomusManager" 
	            accountKey="<accountKey>"
	            accountName="<accountName>"
	            tableName="tomcatSessions"
	            partitionKey="<application name>"/>

Tomcat 5
--------
	1 Please refer the "building instructions" section for building Tomcat-5 specific manager jar file
	2 Copy the manager jar to [CATALINA_HOME]/server/lib
	2 Copy commons-logging and log4j jar files from lib/deploy folder to [CATALINA_HOME]/common/lib 
	3 Copy all of the other jar files from the deploy folder to [CATALINA_HOME]/server/lib
	4 Configuration is as per step '3' in the section above

Building instructions
=====================
The project can be packaged using the ant file build.xml
 
Tomcat 5.5.33, 6, 7
--------------------
Ensure that the 3 argument setAttribute method on line 52 of uk.co.atomus.session.AtomusSession is 
uncommented. Ensure that the versions of catalina.jar, servlet-api.jar and tomcat-coyote.jar referenced 
by the project are the ones in lib/compile

Tomcat 5.5.15
--------------
Ensure that the 3 argument setAttribute method on line 52 of uk.co.atomus.session.AtomusSession is 
commented out. Ensure that the versions of catalina.jar, servlet-api.jar and tomcat-coyote.jar referenced 
by the project are the ones in lib/5.5.15

Persistent Systems Blog:
========================
For more information on this topic please visit "Related posts" section at
http://blog.persistentsys.com/index.php/2012/04/20/cloudninja-for-java/