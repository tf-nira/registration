FROM openjdk:11

# Uncomment below and Comment above line(i.e. FROM openjdk:8) for OS specific (e.g. Alpine OS) docker base image
# FROM openjdk:8-jdk-alpine

# Required build-time arguments
ARG SOURCE                     # Added missing ARG
ARG COMMIT_HASH                # Added missing ARG
ARG COMMIT_ID                  # Added missing ARG
ARG BUILD_TIME                  # Added missing ARG

# Can be passed during Docker build as build time environment for github branch to pick up configuration from.
ARG spring_config_label
# Can be passed during Docker build as build time environment for spring profiles active 
ARG active_profile
# Can be passed during Docker build as build time environment for config server URL 
ARG spring_config_url
# Can be passed during Docker build as build time environment for glowroot 
ARG is_glowroot
# Can be passed during Docker build as build time environment for artifactory URL
ARG artifactory_url

# Environment variable to pass active profile such as DEV, QA, etc. at docker runtime
ENV active_profile_env=${active_profile}
# Environment variable to pass github branch to pick up configuration from at docker runtime
ENV spring_config_label_env=${spring_config_label}
# Environment variable to pass glowroot at docker runtime
ENV is_glowroot_env=${is_glowroot}
# Environment variable to pass artifactory URL at docker runtime
ENV artifactory_url_env=${artifactory_url}

# User configuration arguments
ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

# Update and install dependencies
RUN apt-get -y update \
    && apt-get install -y unzip sudo \
    && groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
    && adduser ${container_user} sudo

# Set working directory for the user
WORKDIR /home/${container_user}

ENV work_dir=/home/${container_user}

# Define loader path
ARG loader_path=${work_dir}/additional_jars/
RUN mkdir -p ${loader_path}

ENV loader_path_env=${loader_path}

# Change volume to whichever storage directory you want to use for this container.
VOLUME ${work_dir}/logs ${work_dir}/Glowroot

# Add the JAR file
ADD ./target/registration-processor-workflow-manager-service-*.jar registration-processor-workflow-manager-service.jar

# Change permissions of files inside working dir
RUN chown -R ${container_user}:${container_user} /home/${container_user}

# Select container user for all tasks
USER ${container_user_uid}:${container_user_gid}

# Add Docker labels
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# CMD instruction to run the application
CMD wget "${artifactory_url_env}"/artifactory/libs-release-local/io/mosip/testing/regproc-reprocessor-ceylon-cache-repo.zip ; \
    wget "${iam_adapter_url_env}" -O "${loader_path_env}"/kernel-auth-adapter.jar; \
    unzip regproc-reprocessor-ceylon-cache-repo.zip ; \
    rm -rf regproc-reprocessor-ceylon-cache-repo.zip ; \
    if [ "$is_glowroot_env" = "present" ]; then \
        wget "${artifactory_url_env}"/artifactory/libs-release-local/io/mosip/testing/glowroot.zip ; \
        unzip glowroot.zip ; \
        rm -rf glowroot.zip ; \
        sed -i 's/<service_name>/registration-processor-workflow-manager-service/g' glowroot/glowroot.properties ; \
        java -jar -javaagent:glowroot/glowroot.jar -Dloader.path="${loader_path_env}" -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" -Dceylon.cache.repo=./regproc-reprocessor-ceylon-cache-repo registration-processor-workflow-manager-service.jar ; \
    else \
        java -Dloader.path="${loader_path_env}" -jar -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" -Dceylon.cache.repo=./regproc-reprocessor-ceylon-cache-repo registration-processor-workflow-manager-service.jar ; \
    fi
