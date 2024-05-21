const glob = require('@actions/glob');
const fs = require('fs');


var patterns = fs.readFileSync("patterns.txt").toString().split("\n")

async function run() {
    
    const globber = await glob.create(patterns.join('\n'))
    const files = await globber.glob()
    // fs.writeFile("result.txt", 
    //     files.map(function(file) {return file.join("\n")}, 
    //                 function(err) {console.log(err ? 'Error :'+err : 'ok') }
    //             )
    // );
    fs.writeFileSync("result.txt", files.join("\n"))

}

run()