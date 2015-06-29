# Jenkins Multi-Branch Project Plugin (mod)

This plugin adds an additional project type that creates sub-projects for each
branch using a shared configuration.

This plugin is a fork of the Jenkins Multi-Branch Project Plugin 
(https://github.com/mjdetullio/multi-branch-project-plugin).
There are two motivations for this fork:

  1. Solve errors and problems of the original plugin without the risk
     of breaking anything that relies on the plugin.
  2. Adapt the plugin to our specific needs, which are beyond the scope
     of the original plugin.
     
Part (1) could possibly be contributed to the original project.

## Usage

Install the plugin using one of these methods:

* Clone this repo, manually compile the HPI file, and upload it through the
Jenkins interface on the "advanced" tab in the plugin update center to get
the newest/unreleased code.

The project type will appear in the list on the "New Job" page.  When
configuring the project, the SCM portion will be different.  This section tells
the project how to find branches to create sub-projects for.  Just about
everything else should look like a normal free-style project and will be
applied to each sub-project.

## Development Instructions

To build the plugin locally:

    mvn clean verify

To release the plugin:

    mvn release:prepare release:perform

To test in a local Jenkins instance:

    mvn hpi:run

## Credits

Thanks to Matthew DeTullio for his work on the original Jenkins Multi-Branch 
Project Plugin (https://github.com/mjdetullio/multi-branch-project-plugin).


## License

Copyright 2015 Zalando SE

Licensed under the MIT license (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.opensource.org/licenses/MIT

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

