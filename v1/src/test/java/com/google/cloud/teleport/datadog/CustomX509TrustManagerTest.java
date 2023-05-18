/*
 * Copyright (C) 2021 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.teleport.datadog;

import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

/** Unit tests for {@link CustomX509TrustManager}. */
@RunWith(JUnit4.class)
public final class CustomX509TrustManagerTest {

  private CustomX509TrustManager customTrustManager;
  private X509Certificate rootCa;
  private X509Certificate recognizedSelfSignedCertificate;
  private X509Certificate unrecognizedSelfSignedCertificate;

  @Before
  public void setUp()
      throws NoSuchAlgorithmException, CertificateException, KeyStoreException, IOException {
    CertificateFactory cf = CertificateFactory.getInstance("X.509");
    ClassLoader classLoader = this.getClass().getClassLoader();
    FileInputStream rootCaInputStream =
        new FileInputStream(classLoader.getResource("PubsubToDatadogTestData/RootCA.crt").getFile());
    FileInputStream recognizedInputStream =
        new FileInputStream(
            classLoader.getResource("PubsubToDatadogTestData/RecognizedCertificate.crt").getFile());
    FileInputStream unrecognizedInputStream =
        new FileInputStream(
            classLoader
                .getResource("PubsubToDatadogTestData/UnrecognizedCertificate.crt")
                .getFile());
    rootCa = (X509Certificate) cf.generateCertificate(rootCaInputStream);
    recognizedSelfSignedCertificate =
        (X509Certificate) cf.generateCertificate(recognizedInputStream);
    unrecognizedSelfSignedCertificate =
        (X509Certificate) cf.generateCertificate(unrecognizedInputStream);

    customTrustManager = new CustomX509TrustManager(rootCa);
  }

  /**
   * Tests whether a recognized (user provided) self-signed certificate is accepted by TrustManager.
   */
  @Test
  public void testCustomX509TrustManagerWithRecognizedCertificate() throws CertificateException {
    customTrustManager.checkServerTrusted(
        new X509Certificate[] {recognizedSelfSignedCertificate}, "RSA");
  }

  /** Tests whether a unrecognized self-signed certificate is rejected by TrustManager. */
  @Test(expected = Exception.class)
  public void testCustomX509TrustManagerWithUnrecognizedCertificate() throws CertificateException {
    customTrustManager.checkServerTrusted(
        new X509Certificate[] {unrecognizedSelfSignedCertificate}, "RSA");
  }
}
