# gitlog-plugin
Generating a git log as a file

Example:

    <plugin>
        <groupId>ru.suntsovto.plugin</groupId>
        <artifactId>gitlog-plugin</artifactId>
        <version>1.0.2</version>
        <configuration>
            <gitFolder>${basedir}/../.git</gitFolder>

            <outputFolder>${project.build.directory}/generated-resources/resources</outputFolder>
            <outputFileName>CHANGELOG.html</outputFileName>

            <template>%ad %h %an %s</template>
            <jiraUrl>http://jira.com</jiraUrl>
            <gitLabUrl>http://gitLab.com</gitLabUrl>
            <countItems>50</countItems>
        </configuration>
        <executions>
            <execution>
                <phase>generate-sources</phase>
                <goals>
                    <goal>generate</goal>
                </goals>
            </execution>
        </executions>
    </plugin>