#!/bin/bash

# =============================================================================
# Graddie V2 - Distribution and Scalability Demonstration Script
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
MAGENTA='\033[0;35m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Configuration
BASE_PORT=2553
WORKER_START_PORT=2554
WEB_PORT=8080
DEMO_WORKERS=3
LOAD_TEST_REQUESTS=5

# Helper functions
print_header() {
    echo -e "\n${MAGENTA}========================================${NC}"
    echo -e "${MAGENTA}$1${NC}"
    echo -e "${MAGENTA}========================================${NC}\n"
}

print_step() {
    echo -e "${CYAN}[STEP]${NC} $1"
}

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

wait_for_input() {
    echo -e "\n${YELLOW}Press Enter to continue...${NC}"
    read -r
}

check_port() {
    local port=$1
    if lsof -Pi :$port -sTCP:LISTEN -t >/dev/null 2>&1; then
        return 0
    else
        return 1
    fi
}

wait_for_port() {
    local port=$1
    local max_attempts=30
    local attempt=0
    
    print_info "Waiting for port $port to be available..."
    while [ $attempt -lt $max_attempts ]; do
        if check_port $port; then
            print_success "Port $port is now available"
            return 0
        fi
        sleep 1
        attempt=$((attempt + 1))
        echo -n "."
    done
    print_error "Timeout waiting for port $port"
    return 1
}

start_coordinator() {
    print_step "Starting Coordinator Node (Port: $BASE_PORT)"
    nohup java -cp target/classes:$(cat target/classpath.txt) \
        com.agentic.GraddieMain $BASE_PORT coordinator > logs/demo_coord.out 2>&1 &
    echo $! > logs/demo_coord.pid
    wait_for_port $BASE_PORT
}

start_worker() {
    local port=$1
    local worker_id=$2
    print_step "Starting Worker Node #$worker_id (Port: $port)"
    nohup java -cp target/classes:$(cat target/classpath.txt) \
        com.agentic.GraddieMain $port worker > logs/demo_worker_$worker_id.out 2>&1 &
    echo $! > logs/demo_worker_$worker_id.pid
    wait_for_port $port
}

start_web_server() {
    print_step "Starting Web Server (Port: $WEB_PORT)"
    nohup java -cp target/classes:$(cat target/classpath.txt) \
        com.agentic.WebServer > logs/demo_web.out 2>&1 &
    echo $! > logs/demo_web.pid
    wait_for_port $WEB_PORT
}

show_cluster_status() {
    print_header "CLUSTER STATUS"
    
    print_info "Active Java Processes:"
    ps aux | grep -E "(GraddieMain|WebServer)" | grep -v grep || print_warning "No processes found"
    
    echo ""
    print_info "Port Bindings:"
    for port in $BASE_PORT $WEB_PORT; do
        if check_port $port; then
            echo -e "  ${GREEN}✓${NC} Port $port: ACTIVE"
        else
            echo -e "  ${RED}✗${NC} Port $port: INACTIVE"
        fi
    done
    
    for i in $(seq 1 $DEMO_WORKERS); do
        local worker_port=$((WORKER_START_PORT + i - 1))
        if check_port $worker_port; then
            echo -e "  ${GREEN}✓${NC} Worker Port $worker_port: ACTIVE"
        else
            echo -e "  ${RED}✗${NC} Worker Port $worker_port: INACTIVE"
        fi
    done
    
    echo ""
    print_info "Log Files:"
    ls -la logs/demo_*.out 2>/dev/null || print_warning "No demo log files found"
}

demonstrate_scaling() {
    print_header "HORIZONTAL SCALING DEMONSTRATION"
    
    print_step "Starting with basic cluster..."
    start_coordinator
    sleep 2
    start_web_server
    sleep 2
    
    print_step "Adding worker nodes one by one..."
    for i in $(seq 1 $DEMO_WORKERS); do
        local worker_port=$((WORKER_START_PORT + i - 1))
        start_worker $worker_port $i
        sleep 2
        
        print_info "Cluster now has $i worker node(s)"
        show_cluster_status
        
        if [ $i -lt $DEMO_WORKERS ]; then
            wait_for_input
        fi
    done
    
    print_success "Horizontal scaling complete! Cluster now has $DEMO_WORKERS workers"
}

test_load_balancing() {
    print_header "LOAD BALANCING DEMONSTRATION"
    
    print_step "Generating test submissions to observe load distribution..."
    
    # Create test data
    cat > test_submission.json << EOF
{
    "studentId": "demo_student_ID",
    "assignment": "Assignment 1",
    "submission": "This is a test submission for load balancing demonstration. The system should distribute this work across available worker nodes.",
    "questionType": "MCQ",
    "correctAnswers": ["A", "B", "C"]
}
EOF

    print_info "Submitting $LOAD_TEST_REQUESTS concurrent requests..."
    
    # Submit multiple requests
    for i in $(seq 1 $LOAD_TEST_REQUESTS); do
        # Replace student ID for each request
        sed "s/demo_student_ID/demo_student_$i/g" test_submission.json > temp_submission_$i.json
        
        print_info "Submitting request $i/LOAD_TEST_REQUESTS..."
        curl -s -X POST http://localhost:$WEB_PORT/grade \
            -H "Content-Type: application/json" \
            -d @temp_submission_$i.json > response_$i.json &
    done
    
    print_info "Waiting for all requests to complete..."
    wait
    
    print_step "Analyzing load distribution in logs..."
    sleep 3
    
    print_info "Worker activity summary:"
    for i in $(seq 1 $DEMO_WORKERS); do
        local worker_port=$((WORKER_START_PORT + i - 1))
        local log_file="logs/demo_worker_$i.out"
        if [ -f "$log_file" ]; then
            local activity_count=$(grep -c "Received.*GradeCategory" "$log_file" 2>/dev/null || echo "0")
            echo -e "  Worker #$i (Port: $worker_port): ${GREEN}$activity_count${NC} tasks processed"
        fi
    done
    
    # Cleanup
    rm -f test_submission.json temp_submission_*.json response_*.json
    
    print_success "Load balancing demonstration complete"
}

demonstrate_communication_patterns() {
    print_header "AKKA COMMUNICATION PATTERNS"
    
    print_step "Monitoring communication patterns in real-time..."
    
    print_info "TELL Pattern (Fire-and-forget):"
    echo "  Location: GradingWorkerActor.java"
    echo "  Usage: Workers sending results back to coordinator"
    
    print_info "ASK Pattern (Request-Response):"
    echo "  Location: GradingCoordinatorActor.java, WebServer.java"
    echo "  Usage: Capacity checks before task distribution"
    
    print_info "FORWARD Pattern (Message Delegation):"
    echo "  Location: SubmissionReceiverActor.java, LLMActor.java"
    echo "  Usage: Message routing with context preservation"
    
    print_step "Submitting a test request to observe patterns..."
    
    # Submit a single request and watch logs
    curl -s -X POST http://localhost:$WEB_PORT/grade \
        -H "Content-Type: application/json" \
        -d '{
            "studentId": "pattern_demo",
            "assignment": "Assignment 2", 
            "submission": "Demonstrate communication patterns",
            "questionType": "Essay"
        }' > pattern_response.json &
    
    print_info "Monitoring logs for 10 seconds..."
    timeout 10s tail -f logs/demo_*.out | head -50 || true
    
    rm -f pattern_response.json
    print_success "Communication patterns demonstrated"
}

show_results() {
    print_header "PROCESSING RESULTS"
    
    print_step "Checking generated results..."
    
    if [ -f "grading_results.csv" ]; then
        print_info "Grading results file found:"
        echo -e "${GREEN}$(wc -l < grading_results.csv)${NC} total entries"
        
        print_info "Latest results:"
        tail -5 grading_results.csv | while IFS=',' read -r line; do
            echo "  $line"
        done
    else
        print_warning "No grading results file found yet"
    fi
    
    print_info "Recent log activity:"
    for log_file in logs/demo_*.out; do
        if [ -f "$log_file" ]; then
            echo -e "\n${BLUE}$(basename $log_file):${NC}"
            tail -3 "$log_file" | sed 's/^/  /'
        fi
    done
}

cleanup_demo() {
    print_header "CLEANING UP DEMONSTRATION"
    
    print_step "Stopping demo processes..."
    
    # Stop processes using PID files
    for pid_file in logs/demo_*.pid; do
        if [ -f "$pid_file" ]; then
            local pid=$(cat "$pid_file")
            if kill -0 "$pid" 2>/dev/null; then
                print_info "Stopping process $pid..."
                kill "$pid"
            fi
            rm -f "$pid_file"
        fi
    done
    
    # Force cleanup any remaining processes
    pkill -f "GraddieMain" 2>/dev/null || true
    pkill -f "WebServer" 2>/dev/null || true
    
    sleep 2
    
    print_step "Cleaning up demo files..."
    rm -f logs/demo_*.out
    rm -f temp_submission_*.json response_*.json pattern_response.json
    
    print_success "Cleanup complete"
}

# Main demonstration flow
main() {
    print_header "GRADDIE V2 - DISTRIBUTION & SCALABILITY DEMO"
    
    print_info "This demonstration will showcase:"
    echo "  • Multi-node Akka cluster deployment"
    echo "  • Horizontal scaling with multiple workers"
    echo "  • Load balancing across worker nodes"
    echo "  • Akka communication patterns (TELL, ASK, FORWARD)"
    echo "  • Real-time monitoring and results"
    
    wait_for_input
    
    # Ensure clean environment
    cleanup_demo 2>/dev/null || true
    
    # Build project
    print_step "Building project..."
    mvn compile -q > /dev/null 2>&1 || {
        print_error "Build failed. Please ensure Maven dependencies are available."
        exit 1
    }
    
    # Generate classpath
    mvn dependency:build-classpath -Dmdep.outputFile=target/classpath.txt -q
    
    # Create logs directory
    mkdir -p logs
    
    # Run demonstrations
    demonstrate_scaling
    wait_for_input
    
    test_load_balancing
    wait_for_input
    
    demonstrate_communication_patterns
    wait_for_input
    
    show_results
    wait_for_input
    
    print_header "DEMONSTRATION COMPLETE"
    print_success "Successfully demonstrated distributed architecture!"
    print_info "Key achievements:"
    echo "  ✓ Multi-node cluster with $DEMO_WORKERS workers"
    echo "  ✓ Load balancing across worker nodes"
    echo "  ✓ All three Akka communication patterns"
    echo "  ✓ External LLM integration"
    echo "  ✓ Real-time scalability"
    
    echo -e "\n${YELLOW}Keep cluster running? (y/N):${NC}"
    read -r keep_running
    
    if [[ "$keep_running" =~ ^[Yy]$ ]]; then
        print_info "Cluster is still running. Access web interface at: http://localhost:$WEB_PORT"
        print_info "Use './run_graddie.sh stop' to stop all nodes later"
    else
        cleanup_demo
    fi
}

# Handle interrupts
trap cleanup_demo INT TERM

# Check if running directly
if [[ "${BASH_SOURCE[0]}" == "${0}" ]]; then
    main "$@"
fi
