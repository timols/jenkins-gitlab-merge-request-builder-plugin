# Gitlab Merge Request Builder Plugin

A plugin that allows Jenkins to build merge requests.

This plugin fetches the source and target branches of a Gitlab merge request and makes them available
to your build via build parameters. Once the build completes, Jenkins will leave a comment on the merge
request indicating whether the merge request was successful.

## Prerequisites

* Whilst there is no explict dependency on the [Git plugin](https://wiki.jenkins-ci.org/display/JENKINS/Git+Plugin),
  it's strongly recommended that you install it since Jenkins will be unable to fetch the source code for you project.

## Installation

* Ensure that a Jenkins user exists within Gitlab and has access to the repository. Ensure that the user
  has **Developer** level access to the project.
* Install the plugin in Jenkins.
    * Currently, the plugin isn't hosted on the Jenkins Plugin repository. This means you'll need to
      clone this repository, build the plugin with (mvn hpi:hpi).
    * Go to ``Jenkins`` -> ``Manage Plugins`` -> ``Advanced`` -> ``Choose File`` -> Select the .hpi file and upload
    * Ensure you restart Jenkins
* Go to ``Manage Jenkins`` -> ``Configure System`` -> ``Gitlab Merge Requests Builder``
* Set the ``Gitlab Host URL`` to the base URL of your Gitlab server
* Set your ``Jenkins Username`` for the Jenkins user (defaults to jenkins)
* Set your ``Jenkins API Token`` for the Jenkins user. This can be found by logging into Gitlab as Jenkins
  and going to the user profile section
* Set/change any of the other available parameters as necessary. If you host Gitlab over an SSL connection
  you may want to enable ignoring certificate errors.
* ``Save`` to preserve your changes.

## Creating a Job

* Create a new job by going to ``New Job``
* Set the ``Project Name``
* Feel free to specify the ``GitHub Project`` url as the url for the Gitlab project (if you have the GitHub plugin installed)
* In the ``SCM`` section:
    * Click ``Git`` and enter your Repositroy URL
    * In ``Branch Specifier`` enter ``origin/${gitlabSourceBranch}`` (if origin corresponds to Gitlab)
    * Open the Advanced options
    * Tick the ``Merge before build`` option and specify the name of the repository as ``origin`` (if origin corresponds to Gitlab)
      and enter the ``Branch to merge to`` as ``${gitlabTargetBranch}``
    * **Ensure ``Prune remote branches before build`` is left unticked**
* In the ``Build Triggers`` section:
    * Check the ``Gitlab Merge Requests Builder``
    * Enter the ``Gitlab Project Path``, this might be something like ``gitlabhq/gitlabhq``
* Configure any other pre build, build or post build actions as necessary
* ``Save`` to preserve your changes

You can trigger a job a manually by clicking ``This build is parameterized`` and adding the relevant build parameters. These include:
* gitlabSourceBranch
* gitlabTargetBranch
* gitlabMergeRequestId

## Contributing

* Check out the latest master to make sure the feature hasn't been implemented or the bug hasn't been fixed yet
* Check out the issue tracker to make sure someone already hasn't requested it and/or contributed it
* Fork the project
* Start a feature/bugfix branch
* Commit and push until you are happy with your contribution
* Make sure to add tests for it. This is important so I don't break it in a future version unintentionally.
* Please try not to mess with the version, or history. If you want to have your own version, or is otherwise necessary, that is fine,
  but please isolate to its own commit so I can cherry-pick around it.

## Copyright

Copyright (c) 2013 Tim Olshansky. See LICENSE for further details.
