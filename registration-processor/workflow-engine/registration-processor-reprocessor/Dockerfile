# Base image: OpenJDK 11
FROM openjdk:11

# Uncomment below and comment above line for Alpine-based image
# FROM openjdk:8-jdk-alpine

# Build-time arguments
ARG spring_config_label
ARG active_profile
ARG spring_config_url
ARG is_glowroot
ARG artifactory_url
ARG iam_adapter_url
ARG container_user=mosip
ARG container_user_group=mosip
ARG container_user_uid=1001
ARG container_user_gid=1001

# Additional build-time arguments for tracking metadata
ARG SOURCE
ARG COMMIT_HASH
ARG COMMIT_ID
ARG BUILD_TIME

# Set environment variables from build-time arguments
ENV active_profile_env=${active_profile}
ENV spring_config_label_env=${spring_config_label}
ENV is_glowroot_env=${is_glowroot}
ENV artifactory_url_env=${artifactory_url}
ENV iam_adapter_url_env=${iam_adapter_url}

# Set working directory and additional directories
ENV work_dir=/home/${container_user}
ENV loader_path=${work_dir}/additional_jars/
ENV loader_path_env=${loader_path}

# Install dependencies and create user
RUN apt-get -y update \
    && apt-get install -y unzip sudo \
    && groupadd -g ${container_user_gid} ${container_user_group} \
    && useradd -u ${container_user_uid} -g ${container_user_group} -s /bin/sh -m ${container_user} \
    && adduser ${container_user} sudo

# Set working directory for the user
WORKDIR ${work_dir}

# Create necessary directories
RUN mkdir -p ${loader_path}

# Set volumes for logs and Glowroot
VOLUME ${work_dir}/logs
VOLUME ${work_dir}/Glowroot

# Add the application JAR
ADD ./target/registration-processor-reprocessor-*.jar registration-processor-reprocessor.jar

# Ensure proper permissions for the working directory
RUN chown -R ${container_user}:${container_user} ${work_dir}

# Switch to the created user
USER ${container_user_uid}:${container_user_gid}

# Add metadata to the image
LABEL source=${SOURCE}
LABEL commit_hash=${COMMIT_HASH}
LABEL commit_id=${COMMIT_ID}
LABEL build_time=${BUILD_TIME}

# Define the entrypoint to download and run the necessary services
CMD wget "${artifactory_url_env}/artifactory/libs-release-local/io/mosip/testing/regproc-reprocessor-ceylon-cache-repo.zip" \
    && unzip regproc-reprocessor-ceylon-cache-repo.zip \
    && rm -rf regproc-reprocessor-ceylon-cache-repo.zip \
    && if [ "$is_glowroot_env" = "present" ]; then \
        wget "${iam_adapter_url_env}" -O "${loader_path_env}/kernel-auth-adapter.jar" \
        && wget "${artifactory_url_env}/artifactory/libs-release-local/io/mosip/testing/glowroot.zip" \
        && unzip glowroot.zip \
        && rm -rf glowroot.zip \
        && sed -i 's/<service_name>/registration-processor-reprocessor/g' glowroot/glowroot.properties \
        && java -jar -Dloader.path="${loader_path_env}" -javaagent:glowroot/glowroot.jar -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" -Dceylon.cache.repo=./regproc-reprocessor-ceylon-cache-repo registration-processor-reprocessor.jar; \
    else \
        wget "${iam_adapter_url_env}" -O "${loader_path_env}/kernel-auth-adapter.jar" \
        && java -Dloader.path="${loader_path_env}" -jar -Dspring.cloud.config.label="${spring_config_label_env}" -Dspring.profiles.active="${active_profile_env}" -Dspring.cloud.config.uri="${spring_config_url_env}" -Dceylon.cache.repo=./regproc-reprocessor-ceylon-cache-repo registration-processor-reprocessor.jar; \
    fi
