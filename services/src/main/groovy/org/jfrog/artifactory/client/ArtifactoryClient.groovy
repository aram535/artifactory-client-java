package org.jfrog.artifactory.client

import groovyx.net.http.ContentType
import groovyx.net.http.EncoderRegistry
import groovyx.net.http.RESTClient
import org.apache.http.entity.InputStreamEntity
import org.jfrog.artifactory.client.impl.ArtifactoryImpl

import static org.apache.http.auth.params.AuthPNames.TARGET_AUTH_PREF
import static org.apache.http.client.params.AuthPolicy.BASIC
import static org.apache.http.client.params.CookiePolicy.IGNORE_COOKIES
import static org.apache.http.client.params.HttpClientParams.setCookiePolicy

/**
 * @author jbaruch
 * @since 12/08/12
 */
public class ArtifactoryClient {

    static Artifactory create(String url, String username = null, String password = null) {
        def matcher = url =~ /(https?:\/\/[^\/]+)\/+([^\/]*).*/
        if (!matcher) {
            matcher = url =~ /(https?:\/\/[^\/]+)\/*()/
            if (!matcher) {
                throw new IllegalArgumentException("Invalid Artifactory URL: ${url}.")
            }
        }
        def client = new RESTClient(matcher[0][1])

        //Set an entity with a length for a stream that has the totalBytes method on it
        def er = new EncoderRegistry() {
            //TODO: [by yl] move totalBytes() to a stream interface that clients can implement
            @Override
            InputStreamEntity encodeStream(Object data, Object contentType) throws UnsupportedEncodingException {
                if (data.metaClass.getMetaMethod('totalBytes')) {
                    InputStreamEntity entity = new InputStreamEntity((InputStream) data, data.totalBytes());
                    if (contentType == null) {
                        contentType = ContentType.BINARY
                    };
                    entity.setContentType(contentType.toString());
                    return entity
                } else if (data instanceof InputStream) {
                    final InputStream stream = (InputStream) data
                    InputStreamEntity entity = new InputStreamEntity(stream, stream.available());
                    if (contentType == null) {
                        contentType = ContentType.BINARY
                    };
                    entity.setContentType(contentType.toString());
                    return entity
                } else {
                    return super.encodeStream(data, contentType)
                }
            }
        }
        client.encoders = er

        client.headers.'User-Agent' = 'Artifactory-Client/1.0'
        def params = client.client.getParams()
        setCookiePolicy params, IGNORE_COOKIES
        if (username && password) {
            params.setParameter(TARGET_AUTH_PREF, [BASIC]);
            client.auth.basic username, password
            //always need preemptive, there are resources that do no challenge and send another response
            //(e.g. list of plugins returns empty list if not authenticated, without challenging.
            client.headers.Authorization = "Basic ${"$username:$password".toString().bytes.encodeBase64()}"
        }
        Artifactory artifactory = new ArtifactoryImpl(client, matcher[0][2])
        artifactory.@username = username
        artifactory
    }
}
