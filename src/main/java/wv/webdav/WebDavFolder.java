/*
 * Copyright (c)  2022 Ward van der Veer.  Licensed under the Apache Licence.
 */

package wv.webdav;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import wv.webdav.jaxb.*;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.*;

public class WebDavFolder extends WebDavItem {

    private Map<String, WebDavFolder> subFolders;
    private Map<String, WebDavFile> files;

    public WebDavFolder(WebDavFolder parent, String name) {
        super(parent, name, new Date());
        subFolders = new HashMap<>();
        files = new HashMap<>();
    }

    public void addFolder(WebDavFolder webDavFolder) {
        subFolders.put(webDavFolder.name, webDavFolder);
        webDavFolder.parent = this;
    }

    /**
     * Retrieve an item (folder or file) contained within this folder
     *
     * @param itemPath path to the item
     * @return the WebDavItem referenced
     * @throws FileNotFoundException if there is no item at the path given
     */
    @NonNull
    public WebDavItem getContainedItem(String[] itemPath) throws FileNotFoundException {
        if (itemPath.length == 0) {
            return this;
        } else if  (itemPath.length > 1) {
            if (subFolders.containsKey(itemPath[0].toLowerCase())) {
                WebDavFolder subFolder = subFolders.get(itemPath[0].toLowerCase());
                String[] subPath = Arrays.copyOfRange(itemPath,  1, itemPath.length);
                return subFolder.getContainedItem(subPath);
            } else {
                throw new FileNotFoundException(itemPath[0]);
            }
        } else {
            if (subFolders.containsKey(itemPath[0].toLowerCase())) {
                return subFolders.get(itemPath[0].toLowerCase());
            } else if (files.containsKey(itemPath[0].toLowerCase())) {
                return files.get(itemPath[0].toLowerCase());
            } else {
                throw new FileNotFoundException(itemPath[0]);
            }
        }
    }

    @Override
    protected ResponseEntity<byte[]> doPropFind(String reqUrl, Depth depth, Object body) {
        try {
            XmlMapper xmlMapper = new XmlMapper();

            Multistatus ms = new Multistatus();
            ms.setResponse(buildPropFindResponses(reqUrl, depth));

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            xmlMapper.writeValue(baos, ms);
            byte[] content = baos.toByteArray();
            HttpHeaders hdrs = new HttpHeaders();
            hdrs.put(HttpHeaders.CONTENT_TYPE, List.of(MediaType.TEXT_XML_VALUE));
            hdrs.put(HttpHeaders.CONTENT_LENGTH, List.of(Integer.toString(content.length)));

            return new ResponseEntity<>(content, hdrs, HttpStatus.OK);

        } catch (IOException e) {
            return buildError(HttpStatus.BAD_REQUEST.value(), e.getMessage());
        }
    }

    protected List<Response> buildPropFindResponses(String reqUrl, Depth depth) throws MalformedURLException {
        List<Response> responses = new ArrayList<>();
        if (!depth.isNoRoot()) {
            responses.add(buildPropFindResponse(reqUrl));
        }
        if (depth != Depth.Zero) {
            for (WebDavFolder wdFolder: subFolders.values()) {
                if (depth == Depth.Infinity || depth == Depth.InfinityNoRoot) {
                    responses.addAll(wdFolder.buildPropFindResponses(reqUrl, Depth.Infinity));
                } else {
                    responses.add(wdFolder.buildPropFindResponse(reqUrl));
                }
            }
            for (WebDavFile wdFile: files.values()) {
                responses.add(wdFile.buildPropFindResponse(reqUrl));
            }
        }
        return responses;
    }

    @Override
    protected Response buildPropFindResponse(String reqUrl) throws MalformedURLException {
        Response response = super.buildPropFindResponse(reqUrl);

        // mark as a collection
        CollectionResourceType resourceType = new CollectionResourceType();
        response.getPropStat().getProp().setResourceType(resourceType);

        return response;
    }

    @NonNull
    protected ResponseEntity<byte[]> doGet(@NonNull String reqUrl) {
        // return the same content as PROPFIND
        return doPropFind(reqUrl, Depth.One, null);
    }

}
