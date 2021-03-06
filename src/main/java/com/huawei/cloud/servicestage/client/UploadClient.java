/**
 * Copyright 2016 - 2018 Huawei Technologies Co., Ltd. All rights reserved.
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
package com.huawei.cloud.servicestage.client;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;

import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.FileEntity;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Client for uploading files to SWR
 * 
 * @author Farhan Arshad
 */
public class UploadClient implements Constants {
    private Logger logger = Util.logger;

    private String apiUrl = null;

    public UploadClient() throws IOException {
        this.apiUrl = ConfigProperties.getProperties()
                .getProperty(ConfigProperties.UPLOAD_API_URL);
    }

    public UploadClient(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public SimpleResponse performGet(String requestEndpoint, Token token)
            throws IOException {
        String requestUrlBase = String.format(apiUrl, token.getRegion());
        String requestUrl = requestUrlBase + requestEndpoint;

        logger.info(requestUrl);

        HttpGet request = new HttpGet(requestUrl);

        // headers
        request.setHeader(CONTENT_TYPE_HEADER_KEY, CONTENT_TYPE_HEADER_VALUE);
        request.setHeader(X_SWR_OVERRIDE_HEADER_KEY,
                X_SWR_OVERRIDE_HEADER_VALUE);
        request.setHeader(X_SWR_DECOMPRESS_HEADER_KEY,
                X_SWR_DECOMPRESS_HEADER_VALUE);
        request.setHeader(AUTHORIZATION_HEADER_KEY,
                AUTHORIZATION_HEADER_VALUE_PREFIX + token.getToken());

        // proxy (if needed)
        Util.setProxy(request);

        // send request
        CloseableHttpResponse response = HttpClients.createDefault()
                .execute(request);

        try {
            HttpEntity entity = response.getEntity();

            // read response - to be returned by method
            String content = Util.convertStreamToString(entity.getContent());

            // consume remaining stream, if any
            EntityUtils.consume(entity);

            logger.info(response.getStatusLine().getStatusCode() + "");
            logger.info(content);

            return new SimpleResponse(response.getStatusLine()
                    .getStatusCode() == HttpStatus.SC_OK, content);
        } finally {
            response.close();
        }
    }

    public Set<String> getRepos(String domain, String namespace, Token token)
            throws IOException {
        SimpleResponse response = performGet("/domains/" + domain
                + "/namespaces/" + namespace + "/repositories/", token);

        if (!response.isOk()) {
            throw new FileNotFoundException(response.getMessage());
        }

        JsonParser parser = new JsonParser();
        JsonArray root = parser.parse(response.getMessage()).getAsJsonArray();

        Set<String> repos = new HashSet<>();
        for (JsonElement e : root) {
            JsonObject repoObject = e.getAsJsonObject();
            String name = repoObject.get("name").getAsString();

            repos.add(name);
        }

        return repos;
    }

    public Set<String> getPackages(String domain, String namespace, String repo,
            Token token) throws IOException {
        SimpleResponse response = performGet(
                "/domains/" + domain + "/namespaces/" + namespace
                        + "/repositories/" + repo + "/packages",
                token);

        if (!response.isOk()) {
            throw new FileNotFoundException(response.getMessage());
        }

        JsonParser parser = new JsonParser();
        JsonArray root = parser.parse(response.getMessage()).getAsJsonArray();

        Set<String> packages = new HashSet<>();
        for (JsonElement e : root) {
            JsonObject packageObject = e.getAsJsonObject();
            String name = packageObject.get("package").getAsString();

            packages.add(name);
        }

        return packages;
    }

    public Set<String> getVersions(String domain, String namespace, String repo,
            String packageName, Token token) throws IOException {
        SimpleResponse response = performGet("/domains/" + domain
                + "/namespaces/" + namespace + "/repositories/" + repo
                + "/packages/" + packageName + "/versions", token);

        if (!response.isOk()) {
            throw new FileNotFoundException(response.getMessage());
        }

        JsonParser parser = new JsonParser();
        JsonArray root = parser.parse(response.getMessage()).getAsJsonArray();

        Set<String> versions = new HashSet<>();
        for (JsonElement e : root) {
            JsonObject versionObject = e.getAsJsonObject();
            String name = versionObject.get("version").getAsString();

            versions.add(name);
        }

        return versions;
    }

    public String getExternalUrl(String domain, String namespace, String repo,
            String packageName, String version, String filename, Token token)
            throws IOException {
        SimpleResponse response = performGet(
                "/domains/" + domain + "/namespaces/" + namespace
                        + "/repositories/" + repo + "/packages/" + packageName
                        + "/versions/" + version + "/file_paths/",
                token);

        if (!response.isOk()) {
            throw new FileNotFoundException(response.getMessage());
        }

        JsonParser parser = new JsonParser();
        JsonArray root = parser.parse(response.getMessage()).getAsJsonArray();

        for (JsonElement e : root) {
            JsonObject fileObject = e.getAsJsonObject();
            if (fileObject.get("filename").getAsString().equals(filename)) {
                String url = fileObject.get("external_url").getAsString();
                return url;
            }
        }

        throw new FileNotFoundException(response.getMessage());
    }

    /**
     * @param localFilePath
     *            absolute path to the file
     * @param domain
     * @param namespace
     * @param repo
     * @param packageName
     * @param version
     * @param name
     * @param token
     * @return
     * @throws IOException
     */
    public SimpleResponse upload(String localFilePath, String domain,
            String namespace, String repo, String packageName, String version,
            String name, Token token) throws IOException {
        String baseRequestUrl = String.format(apiUrl, token.getRegion());
        String requestUrl = baseRequestUrl + "/domains/" + domain
                + "/namespaces/" + namespace + "/repositories/" + repo
                + "/packages/" + packageName + "/versions/" + version
                + "/file_paths/" + name;

        logger.info(requestUrl);

        HttpPut request = new HttpPut(requestUrl);

        // proxy (if needed)
        Util.setProxy(request);

        // file
        File file = new File(localFilePath);
        request.setEntity(new FileEntity(file));

        // headers
        request.setHeader(CONTENT_TYPE_HEADER_KEY, CONTENT_TYPE_HEADER_VALUE);
        request.setHeader(X_SWR_OVERRIDE_HEADER_KEY,
                X_SWR_OVERRIDE_HEADER_VALUE);
        request.setHeader(X_SWR_DECOMPRESS_HEADER_KEY,
                X_SWR_DECOMPRESS_HEADER_VALUE);
        request.setHeader(AUTHORIZATION_HEADER_KEY,
                AUTHORIZATION_HEADER_VALUE_PREFIX + token.getToken());

        // perform request
        CloseableHttpResponse response = HttpClients.createDefault()
                .execute(request);

        try {
            HttpEntity entity = response.getEntity();

            // read response
            String content = Util.convertStreamToString(entity.getContent());

            // try to pretty-fy if content is json
            try {
                JsonObject json = new JsonParser().parse(content)
                        .getAsJsonObject();
                content = new GsonBuilder().setPrettyPrinting().create()
                        .toJson(json);
            } catch (Exception e) {
            }

            // consume remaining stream, if any
            EntityUtils.consume(entity);

            logger.info(response.getStatusLine().getStatusCode() + "");
            logger.info(content);

            if (response.getStatusLine()
                    .getStatusCode() == HttpStatus.SC_CREATED) {
                return new SimpleResponse(true, content);
            } else {
                return new SimpleResponse(false, content);
            }
        } finally {
            response.close();
        }
    }

}
