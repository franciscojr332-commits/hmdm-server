/*
 *
 * Headwind MDM: Open Source Android MDM Software
 * https://h-mdm.com
 *
 * Copyright (C) 2019 Headwind Solutions LLC (http://h-sms.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hmdm.rest.json;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import java.io.Serializable;

/**
 * Represents a file present on the server in the files directory
 * (/usr/local/tomcat/work/files/) for listing and selection without upload.
 */
@ApiModel(description = "A file on the server files directory")
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerFileItem implements Serializable {

    private static final long serialVersionUID = 1L;

    @ApiModelProperty("Relative path/name of the file (e.g. Waze.xapk)")
    private String filePath;
    @ApiModelProperty("File name")
    private String name;
    @ApiModelProperty("Size in bytes")
    private long size;

    public ServerFileItem() {
    }

    public ServerFileItem(String filePath, String name, long size) {
        this.filePath = filePath;
        this.name = name;
        this.size = size;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }
}
