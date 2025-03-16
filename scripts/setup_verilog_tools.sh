#!/bin/bash
# Setup script for Verilog generation tools

# Create the scripts directory if it doesn't exist
mkdir -p scripts

# Make the Python script executable
chmod +x scripts/split_verilog.py

# Create a bin directory for additional tools
mkdir -p bin

cat > bin/view_modules.py << 'EOF'
#!/usr/bin/env python3
"""
A simple TUI to navigate and view generated Verilog modules.
"""
import os
import sys
import curses
import re

def get_module_files():
    """Get all Verilog module files in the generated directory."""
    if not os.path.exists('generated'):
        print("Error: 'generated' directory not found.")
        sys.exit(1)
        
    files = []
    for file in os.listdir('generated'):
        if file.endswith('.v') and not file == 'combined_uart.v' and not file == 'all_modules.v':
            files.append(file)
    return sorted(files)

def read_file_content(filename):
    """Read the content of a file."""
    path = os.path.join('generated', filename)
    try:
        with open(path, 'r') as f:
            return f.read()
    except Exception as e:
        return f"Error reading file: {str(e)}"

def syntax_highlight(text):
    """Simple syntax highlighting for Verilog."""
    lines = text.split('\n')
    result = []
    
    for line in lines:
        # Comment lines
        if re.match(r'^\s*\/\/', line):
            result.append(('comment', line))
        # Module declaration
        elif re.match(r'^\s*module\s+', line):
            result.append(('keyword', line))
        # Other keywords
        elif any(keyword in line for keyword in ['input', 'output', 'wire', 'reg', 'assign', 'always', 'endmodule']):
            result.append(('keyword', line))
        else:
            result.append(('normal', line))
    
    return result

def main(stdscr):
    # Setup colors
    curses.start_color()
    curses.use_default_colors()
    curses.init_pair(1, curses.COLOR_WHITE, -1)  # Normal text
    curses.init_pair(2, curses.COLOR_GREEN, -1)  # Keywords
    curses.init_pair(3, curses.COLOR_CYAN, -1)   # Comments
    curses.init_pair(4, curses.COLOR_YELLOW, -1) # Selected item
    
    # Get files
    files = get_module_files()
    
    if not files:
        stdscr.addstr(0, 0, "No Verilog files found in 'generated' directory.")
        stdscr.refresh()
        stdscr.getch()
        return
    
    # Initialize
    current_file = 0
    current_line = 0
    mode = "list"  # list or view
    
    while True:
        stdscr.clear()
        height, width = stdscr.getmaxyx()
        
        if mode == "list":
            # Draw file list
            stdscr.addstr(0, 0, "Verilog Modules:", curses.A_BOLD)
            stdscr.addstr(1, 0, "Press Enter to view a module, q to quit")
            
            list_height = height - 3
            start_idx = max(0, current_file - list_height // 2)
            end_idx = min(len(files), start_idx + list_height)
            
            for i in range(start_idx, end_idx):
                y = i - start_idx + 3
                if i == current_file:
                    stdscr.addstr(y, 0, "> " + files[i], curses.color_pair(4) | curses.A_BOLD)
                else:
                    stdscr.addstr(y, 0, "  " + files[i])
        
        elif mode == "view":
            # Draw file content
            content = read_file_content(files[current_file])
            highlighted = syntax_highlight(content)
            
            # Title
            stdscr.addstr(0, 0, f"File: {files[current_file]}", curses.A_BOLD)
            stdscr.addstr(1, 0, "Press Escape to return to list, q to quit")
            
            view_height = height - 3
            total_lines = len(highlighted)
            
            # Adjust scroll if needed
            if current_line >= total_lines:
                current_line = max(0, total_lines - 1)
                
            # Calculate visible range
            start_line = max(0, current_line - view_height // 2)
            end_line = min(total_lines, start_line + view_height)
            
            for i in range(start_line, end_line):
                y = i - start_line + 3
                line_type, line_text = highlighted[i]
                
                # Set color based on line type
                if line_type == 'keyword':
                    color = curses.color_pair(2)
                elif line_type == 'comment':
                    color = curses.color_pair(3)
                else:
                    color = curses.color_pair(1)
                
                # Highlight current line
                if i == current_line:
                    color |= curses.A_REVERSE
                
                # Truncate if line is too long
                if len(line_text) > width - 1:
                    line_text = line_text[:width-4] + "..."
                    
                stdscr.addstr(y, 0, line_text, color)
        
        stdscr.refresh()
        
        # Handle input
        key = stdscr.getch()
        
        if key == ord('q'):
            break
            
        elif mode == "list":
            if key == curses.KEY_DOWN and current_file < len(files) - 1:
                current_file += 1
            elif key == curses.KEY_UP and current_file > 0:
                current_file -= 1
            elif key == curses.KEY_ENTER or key == 10:  # Enter key
                mode = "view"
                current_line = 0
                
        elif mode == "view":
            content = read_file_content(files[current_file])
            highlighted = syntax_highlight(content)
            total_lines = len(highlighted)
            
            if key == curses.KEY_DOWN and current_line < total_lines - 1:
                current_line += 1
            elif key == curses.KEY_UP and current_line > 0:
                current_line -= 1
            elif key == curses.KEY_NPAGE:  # Page Down
                current_line = min(total_lines - 1, current_line + view_height)
            elif key == curses.KEY_PPAGE:  # Page Up
                current_line = max(0, current_line - view_height)
            elif key == 27:  # Escape key
                mode = "list"

if __name__ == "__main__":
    try:
        curses.wrapper(main)
    except KeyboardInterrupt:
        pass
EOF

chmod +x bin/view_modules.py

# Create a helper script to quickly view a specific module
cat > bin/view_module.sh << 'EOF'
#!/bin/bash
# View a specific Verilog module

if [ $# -eq 0 ]; then
    echo "Usage: $0 <module_name>"
    echo "Example: $0 Uart"
    exit 1
fi

MODULE="$1"
MODULE_FILE="generated/${MODULE}.v"

if [ ! -f "$MODULE_FILE" ]; then
    echo "Module '$MODULE' not found!"
    echo "Available modules:"
    ls -1 generated/*.v | grep -v "combined_uart.v\|all_modules.v" | sed 's/generated\///' | sed 's/\.v$//'
    exit 1
fi

# Check for syntax highlighting tools
if command -v bat &>/dev/null; then
    bat --language=verilog "$MODULE_FILE"
elif command -v highlight &>/dev/null; then
    highlight -O ansi --syntax=verilog "$MODULE_FILE" | less -R
else
    less "$MODULE_FILE"
fi
EOF

chmod +x bin/view_module.sh

echo "Setup complete!"
echo "To generate and split Verilog files, run: make verilog"
echo "To browse the generated modules, run: python bin/view_modules.py"
echo "To view a specific module, run: bin/view_module.sh <module_name>"