// This file is used for organizational scans
import static java.util.UUID.randomUUID
import groovy.transform.Field

@Field def jenkins_creds = 'YOUR_CREDENTIALSID_HERE'
@Field String branch_name = env.BRANCH_NAME
@Field String company_suff = 'example.com'

def sh_out(cmd){
      sh(returnStdout:true, script: cmd).trim()
}

def getCommitter(){
	withCredentials([
	[$class: 'UsernamePasswordMultiBinding', credentialsId: jenkins_creds, usernameVariable: 'J_USER', passwordVariable: 'J_PASS'],
	]){
		committer_cmd = """ curl -s -u ${J_USER}:${J_PASS} ${env.BUILD_URL}api/json | python -mjson.tool | grep abso | awk 'NR==1' | cut -d/ -f5 | tr -d '"' | tr -d ',' | tr -d '\n' """
		cmtr = sh_out(committer_cmd)
		return cmtr
	}
}

def getRepo(){
	withCredentials([
    [$class: 'UsernamePasswordMultiBinding', credentialsId: jenkins_creds, usernameVariable: 'J_USER', passwordVariable: 'J_PASS'],
    ]){
		repo_cmd = """ curl -s -u \${J_USER}:\${J_PASS} ${env.BUILD_URL}api/json | python -mjson.tool | grep url | awk 'NR==2' | cut -d/ -f7 """
		repo = sh_out(repo_cmd)
		return repo
    }
}

def notifyEmail(String result, String to, def stage_name=null, def errorMessage=null){
	withCredentials([
	[$class: 'UsernamePasswordMultiBinding', credentialsId: jenkins_creds, usernameVariable: 'J_USER', passwordVariable: 'J_PASS'],
	]){
		def committer = getCommitter()
		print "New function - getCommitter: "+committer
		def repo = getRepo()
		print "New function - getRepo: "+repo
		subject = "${result} building repository $repo branch \"$branch_name\" build #${env.BUILD_ID}!"
		job_console = "${env.JOB_URL}${env.BUILD_NUMBER}/console"
		
		String jlink = "(<${env.BUILD_URL}|Open>)"
		emailadd = committer+'@'+company_suff
	}
    def body = """
              <!DOCTYPE html>
              <html>
              <body>
              <h2> Hi, Jenkins here! </h2>
                          <p><b> Result for branch: </b> $branch_name </p>
                          <p><b> Job URL for more information: <a href=$job_console target="_blank">$job_console</a></b>
                  </body>
          </html>
      """

      //In case of failure - the mail will include the step name that failed
      if (stage_name) {
          body = body.replaceAll("</body>",
                "<p><b> Pipeline failed at stage: </b> ${stage_name}</p></body>")
      }

      if (errorMessage) {
          body = body.replaceAll("</body>",
                "<p><b> The exception was: </b> ${errorMessage}</p></body>")
      }

      //code below sends email using subject and body defined above
          emailext attachLog: true, body: body, subject: subject,
          recipientProviders: [[$class: 'RequesterRecipientProvider']],
          to: committer, mimeType: 'text/html', compressLog: false
}

def run_in_stage(String stage_name, Closure command, String sendTo){

    String ulink = getCommitter()
    String jlink = "(<${env.BUILD_URL}|Open>)"

  	println "============================================================"
	stage (stage_name) {
		try {
			command()
				if (currentBuild.result == 'FAILURE') {
				    error "Build failed, see log for further details."
				}
			println "============================================================"
		} catch (Exception ex) {
			def except = "${ex}"
			String emailadd = ulink+'@'+company_suff
			if (currentBuild.result == null) {
				currentBuild.result = "FAILURE"
			}
			this.notifyStatus(stage_name, currentBuild.result)
			echo "Pipeline failed at stage: ${stage_name}"
			throw ex
		}
	}
}

def notifyStatus(stage_name, result) {
	ulink = getCommitter()
	jlink = "(<${env.BUILD_URL}|Open>)"
	emailadd = ulink+'@'+company_suff
	print "!NS ulink:"+ ulink
	print "!NS emailadd:"+ emailadd

	if(result=='SUCCESS') {
		color = 'good'
			if(ulink){
				message = "Job: *${env.JOB_NAME}* Finished Successfuly! :thumbsup: Started by: <@${ulink} (<!here|here>) Log: ${jlink}>"
			} else {
				message = "Job: *${env.JOB_NAME}* Finished Successfuly! :thumbsup: Started by: <'Branch Indexing or Manual run' (<!here|here>) Log: ${jlink}>"
			}
	}	else {
		color = 'danger'
			if(ulink){
				message = "Job: *${env.JOB_NAME}* Failed! :thumbsdown: Started by: <@${ulink} (<!here|here>) Log: ${jlink}>"
			} else {
				message = "Job: *${env.JOB_NAME}* Failed! :thumbsdown: Started by: <'Branch Indexing or Manual run' (<!here|here>) Log: ${jlink}>"
			}
	}
	
	notifyEmail(currentBuild.result, emailadd, stage_name)
	notifySlack(channel, color, message)
}

def notifySlack(String channel, String color, String teamDomain=null, String token=null, String message) {
    slackSend channel: channel, color: "${color}", teamDomain: teamDomain, token: token,
    message: message
}

return this;
