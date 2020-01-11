package com.ken.security;

import org.apache.geode.distributed.DistributedMember;
import org.apache.geode.internal.logging.LogService;
import org.apache.geode.security.AuthInitialize;
import org.apache.geode.security.AuthenticationFailedException;
import org.apache.logging.log4j.Logger;

import java.util.Properties;

public class AuthenticationUtil implements AuthInitialize {
  private static final Logger logger = LogService.getLogger();

  private static final String USER_NAME = "security-username";
  private static final String PASSWORD = "security-password";

  @Override
  public void close() {
  }

  @Override
  public Properties getCredentials(Properties securityProps, DistributedMember server, boolean isPeer)
          throws AuthenticationFailedException {
    Properties credentials = new Properties();
    String userName = securityProps.getProperty(USER_NAME);
    if (userName == null) {
      throw new AuthenticationFailedException(
              "AuthenticationUtil: user name property [" + USER_NAME + "] not set.");
    }
    credentials.setProperty(USER_NAME, userName);
    credentials.setProperty(PASSWORD, System.getProperty(PASSWORD));
    logger.info("AuthenticationUtil: successfully obtained credentials for user " + userName);
    return credentials;
  }
}
