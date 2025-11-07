#!/bin/bash

# Function: Change to specified directory and execute command
# Parameters:
#   $1 - Directory path
#   $2 - Command to execute
run_in_path() {
    local path="$1"
    local command="$2"

    echo "enter directory: $path"
    echo "execute command: $command"

    # Save current directory and change to target path
    local original_dir=$(pwd)
    cd "$path" && eval "$command"
    
    # Return to original directory
    cd "$original_dir"
}

# Function: Display comprehensive help information
show_help() {
  echo "Build Usage:"
  echo "./build.sh                  //build all modules"
  echo "./build.sh clean            //clean all modules"
  echo "./build.sh module_name      //build one module"
  echo ""
}


# Main execution logic
if [ $# -eq 0 ]; then
    # No arguments provided - build all modules
    run_in_path "pc-common" "mvn clean install -DskipTests"
    run_in_path "pc-pms" "mvn clean package -Dtar -DskipTests"
    run_in_path "pc-pcp" "mvn clean package -Pdist assembly:single -DskipTests -Dtar"
    run_in_path "sdk/pc-sdk-java" "mvn clean install -DskipTests"
    run_in_path "pc-test" "mvn clean package -Pdist assembly:single -DskipTests -Dtar"

else
    # Handle different command line arguments
    if [ "$1" == "clean" ]; then
        # Clean all modules
        run_in_path "pc-common" "mvn clean"
        run_in_path "pc-pms" "mvn clean"
        run_in_path "pc-pcp" "mvn clean"
        run_in_path "sdk/pc-sdk-java" "mvn clean"
        run_in_path "pc-test" "mvn clean"

    elif [ "$1" == "help" ]; then
        show_help
    elif [ "$1" == "pc-common" ]; then
        run_in_path "pc-common" "mvn clean install -DskipTests"
    elif [ "$1" == "pc-pms" ]; then
        run_in_path "pc-pms" "mvn clean package -Dtar -DskipTests"
    elif [ "$1" == "pc-pcp" ]; then
        run_in_path "pc-pcp" "mvn clean package -Pdist assembly:single -DskipTests -Dtar"
    elif [ "$1" == "pc-sdk-java" ]; then
        run_in_path "sdk/pc-sdk-java" "mvn clean install -DskipTests"
    elif [ "$1" == "pc-test" ]; then
        run_in_path "pc-test" "mvn clean package -Pdist assembly:single -DskipTests -Dtar"
    else
        echo "invalid input $1!"
    fi
fi

