/*
 * Copyright (c) 2025 Yangagile. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package main

import (
	"flag"
	"fmt"
	"os"
	"strings"
)

// Config represents the application configuration
var globalConfig *Config

// CommandHandler defines the function type for command handlers
type CommandHandler func(config *Config, args []string) error

// Command represents a CLI command
type Command struct {
	Name        string
	Description string
	Usage       string
	Handler     CommandHandler
	Flags       *flag.FlagSet
}

// Command registry
var commands = make(map[string]*Command)

func main() {
	// Load global configuration from file
	globalConfig = NewConfig()

	// Register all available commands
	registerCommands()

	// Check if sufficient arguments are provided
	if len(os.Args) < 2 {
		showHelp()
		return
	}

	// Extract command name from arguments
	commandName := os.Args[1]

	// Handle help commands
	if commandName == "help" || commandName == "-h" || commandName == "--help" {
		if len(os.Args) > 2 {
			showCommandHelp(os.Args[2])
		} else {
			showHelp()
		}
		return
	}

	// Look up and execute the requested command
	cmd, exists := commands[commandName]
	if !exists {
		fmt.Printf("Error: Unknown command '%s'\n\n", commandName)
		showHelp()
		os.Exit(1)
	}

	// Parse command-specific flags
	err := cmd.Flags.Parse(os.Args[2:])
	if err != nil {
		fmt.Printf("Error parsing arguments: %v\n", err)
		showCommandHelp(commandName)
		os.Exit(1)
	}

	// Execute the command handler
	err = cmd.Handler(globalConfig, cmd.Flags.Args())
	if err != nil {
		fmt.Printf("Execution error: %v\n", err)
		os.Exit(1)
	}
}

// registerCommands registers all available commands
func registerCommands() {
	commands["put"] = CreatePutCommand(globalConfig)
	commands["get"] = CreateGetCommand(globalConfig)
	commands["sync"] = CreateSyncCommand(globalConfig)
}

// showHelp displays the main help information
func showHelp() {
	fmt.Println("Usage: pcmd <command> [options] [arguments]")
	fmt.Println()
	fmt.Println("Available commands:")

	// Calculate maximum command name length for alignment
	maxNameLen := 0
	for _, cmd := range commands {
		if len(cmd.Name) > maxNameLen {
			maxNameLen = len(cmd.Name)
		}
	}

	// Display all registered commands
	for _, cmd := range commands {
		padding := strings.Repeat(" ", maxNameLen-len(cmd.Name))
		fmt.Printf("  %s%s '%s' #%s\n", cmd.Name, padding, cmd.Usage, cmd.Description)
	}

	fmt.Println()
	fmt.Println("Use 'pcmd help <command>' to view detailed command help")
	fmt.Println("Use 'pcmd -h <command>' to view command options")
	fmt.Println()
	fmt.Println("Global configuration is loaded from ~/.pcmd.cfg")
}

// showCommandHelp displays detailed help for a specific command
func showCommandHelp(commandName string) {
	cmd, exists := commands[commandName]
	if !exists {
		fmt.Printf("Error: Unknown command '%s'\n", commandName)
		return
	}

	fmt.Printf("Command: %s\n", cmd.Name)
	fmt.Printf("Description: %s\n", cmd.Description)
	fmt.Printf("Usage: app %s\n", cmd.Usage)
	fmt.Println("Options:")
	cmd.Flags.PrintDefaults()
}
