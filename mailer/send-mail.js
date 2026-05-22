const fs = require('fs');
const nodemailer = require('nodemailer');

const readStdin = () => fs.readFileSync(0, 'utf8');

const splitCc = (cc) => {
  if (!cc) return undefined;
  return cc.split(',').map((value) => value.trim()).filter(Boolean);
};

const main = async () => {
  const payload = JSON.parse(readStdin() || '{}');


  if (!username || !password) {
    throw new Error('MAIL_USERNAME and MAIL_PASSWORD are required for Nodemailer.');
  }

  const transporter = nodemailer.createTransport({
    host: "smtp.gmail.com",
    port: 465,
    secure: true,
    
    auth: {
      user: process.env.MAIL_USERNAME,
      pass: process.env.MAIL_PASSWORD,
    },
  });

  const attachments = [];
  if (payload.attachmentPath && fs.existsSync(payload.attachmentPath)) {
    attachments.push({
      filename: payload.attachmentName || payload.attachmentPath.split(/[\\/]/).pop(),
      path: payload.attachmentPath,
    });
  }

  await transporter.sendMail({
    from: process.env.MAIL_FROM,
    to: payload.toEmail,
    cc: splitCc(payload.cc),
    subject: payload.subject,
    text: payload.text,
    html: payload.html,
    attachments,
  });

  process.stdout.write(JSON.stringify({ ok: true }));
};

main().catch((error) => {
  process.stderr.write(error.stack || error.message);
  process.exit(1);
});
