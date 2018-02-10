# test on x86_64 uses Oracle Linux but we do not have this for ppc64le
# so use some other Linux where OpenJDK works 
# FROM oraclelinux:7.2
FROM ppc64le/ubuntu

COPY /jdk /jdk

ENV JAVA_HOME=/jdk

CMD ["/bin/bash"]
